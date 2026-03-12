package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.dto.RosterEntryDto;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.RoomRoster;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.RoomRosterRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.CsvColumnResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RosterService {

    private final RoomRepository roomRepository;
    private final RoomRosterRepository rosterRepository;
    private final StudentRepository studentRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CsvColumnResolver csvColumnResolver;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CsvImportService csvImportService;


    @Autowired
    @Qualifier("uploadExecutor")
    private Executor uploadExecutor;

    /**
     * Accepts the CSV file, returns a jobId immediately, processes async.
     * Reuses the same Redis-based job pattern as AudienceCsvController.
     */
    public String startRosterUpload(Long roomId, String day, MultipartFile file) throws Exception {
        // Verify room exists
        roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

        String jobId = UUID.randomUUID().toString();
        byte[] bytes = file.getBytes();

        redisTemplate.opsForValue()
                .set("upload:job:" + jobId, "PROCESSING", 2, TimeUnit.HOURS);

        CompletableFuture.runAsync(
                () -> processRosterCsv(jobId, roomId, day, bytes),
                uploadExecutor
        );

        return jobId;
    }

    private void processRosterCsv(String jobId, Long roomId, String day, byte[] bytes) {
        List<ImportResult.RowError> rowErrors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(bytes)))) {

                String headerLine = reader.readLine();
                if (headerLine == null) { storeError(jobId, "Empty file"); return; }

                String[] headers = headerLine.split(",");
                Map<String, Integer> indexMap = resolveHeaders(headers);

                // Resolve regNo column — support both "Regd. No" → "regdno" and "RegNo" → "regno"
                Integer regNoIdx = indexMap.get("regdno") != null ? indexMap.get("regdno") : indexMap.get("regno");
                Integer nameIdx  = indexMap.get("fullname") != null ? indexMap.get("fullname") : indexMap.get("name");
                Integer branchIdx = indexMap.get("branch");

                if (regNoIdx == null) { storeError(jobId, "Missing required column: Regd. No"); return; }
                if (nameIdx == null)  { storeError(jobId, "Missing required column: Full Name"); return; }
                if (branchIdx == null){ storeError(jobId, "Missing required column: BRANCH"); return; }

                // Pre-load existing regNos for duplicate detection within batch
                Set<String> seenInBatch = new HashSet<>();
                List<String> validBranches = csvColumnResolver.getValidBranches();

                record ParsedRow(int rowNum, String regNo, String name, String branch) {}
                List<ParsedRow> toProcess = new ArrayList<>();

                String line;
                int rowNum = 1;
                while ((line = reader.readLine()) != null) {
                    rowNum++;
                    if (line.isBlank()) continue;
                    String[] cols = line.split(",", -1);

                    String regNo  = getCol(cols, regNoIdx);
                    String name   = getCol(cols, nameIdx);
                    String branch = getCol(cols, branchIdx);

                    if (regNo == null || name == null || branch == null) {
                        rowErrors.add(ImportResult.RowError.builder().row(rowNum).reason("Missing required fields").build());
                        skipped++; continue;
                    }

                    regNo = regNo.toLowerCase();
                    String branchUpper = branch.toUpperCase();

                    if (!validBranches.contains(branchUpper)) {
                        rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                                .reason("Invalid branch '" + branch + "'. Allowed: " + validBranches).build());
                        skipped++; continue;
                    }

                    if (seenInBatch.contains(regNo)) {
                        rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                                .reason("Duplicate regNo in file: " + regNo).build());
                        skipped++; continue;
                    }

                    seenInBatch.add(regNo);
                    toProcess.add(new ParsedRow(rowNum, regNo, name, branchUpper));
                }

                // Bulk-fetch which regNos already exist in DB
                Set<String> existingRegNos = studentRepository.findAllRegNos();

                // Create missing students (parallel BCrypt)
                List<ParsedRow> newStudentRows = toProcess.stream()
                        .filter(r -> !existingRegNos.contains(r.regNo()))
                        .toList();

                if (!newStudentRows.isEmpty()) {
                    List<Student> toSave = newStudentRows.parallelStream().map(r ->
                            Student.builder()
                                    .regNo(r.regNo())
                                    .name(r.name())
                                    .degree(r.branch())
                                    .passwordHash(passwordEncoder.encode(r.regNo()))
                                    .role("AUDIENCE")
                                    .isPromoted(false)
                                    .build()
                    ).collect(java.util.stream.Collectors.toList());
                    csvImportService.saveStudents(toSave);
                }

                // Now upsert all rows into roster
                for (ParsedRow row : toProcess) {
                    Student student = studentRepository.findByRegNo(row.regNo())
                            .orElse(null);
                    if (student == null) {
                        rowErrors.add(ImportResult.RowError.builder()
                                .row(row.rowNum()).reason("Failed to create student: " + row.regNo()).build());
                        skipped++; continue;
                    }

                    if (!rosterRepository.existsByRoomIdAndStudentIdAndDay(roomId, student.getId(), day)) {
                        rosterRepository.save(RoomRoster.builder()
                                .room(room).student(student).day(day).build());
                        imported++;
                    } else {
                        skipped++;
                    }
                }
            }

            ImportResult result = ImportResult.builder()
                    .imported(imported).skipped(skipped)
                    .errors(rowErrors.size()).rowErrors(rowErrors).build();

            redisTemplate.opsForValue()
                    .set("upload:job:" + jobId, objectMapper.writeValueAsString(result), 2, TimeUnit.HOURS);

        } catch (Exception e) {
            storeError(jobId, e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    /**
     * Get status of a roster upload job (same polling pattern as audience CSV).
     */
    public Map<String, Object> getUploadStatus(String jobId) {
        String val = redisTemplate.opsForValue().get("upload:job:" + jobId);
        if (val == null) return Map.of("status", "NOT_FOUND");
        if ("PROCESSING".equals(val)) return Map.of("status", "PROCESSING");
        if (val.startsWith("ERROR:"))
            return Map.of("status", "ERROR", "message", val.substring(6));
        try {
            ImportResult result = objectMapper.readValue(val, ImportResult.class);
            return Map.of("status", "DONE", "result", result);
        } catch (Exception e) {
            return Map.of("status", "ERROR", "message", "Failed to read result");
        }
    }

    @Transactional(readOnly = true)
    public Page<RosterEntryDto> getRoster(Long roomId, String day, Pageable pageable) {
        return rosterRepository.findByRoomIdAndDay(roomId, day, pageable)
                .map(rr -> RosterEntryDto.builder()
                        .id(rr.getId())
                        .studentId(rr.getStudent().getId())
                        .regNo(rr.getStudent().getRegNo())
                        .name(rr.getStudent().getName())
                        .degree(rr.getStudent().getDegree())
                        .day(rr.getDay())
                        .uploadedAt(rr.getUploadedAt())
                        .build());
    }

    @Transactional
    public void clearRoster(Long roomId, String day) {
        rosterRepository.deleteByRoomIdAndDay(roomId, day);
    }

    /** Resolve CSV headers to field-index map (case-insensitive). */
    private Map<String, Integer> resolveHeaders(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            map.put(h, i);
        }
        return map;
    }

    private void storeError(String jobId, String msg) {
        redisTemplate.opsForValue()
                .set("upload:job:" + jobId, "ERROR:" + msg, 2, TimeUnit.HOURS);
    }

    private String getCol(String[] cols, int idx) {
        if (idx >= cols.length) return null;
        String val = cols[idx].trim();
        return val.isEmpty() ? null : val;
    }

}
