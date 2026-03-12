package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.dto.RosterEntryDto;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.RoomRoster;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.RoomRosterRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
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
                if (headerLine == null) {
                    storeError(jobId, "Empty file");
                    return;
                }

                // Resolve columns (case-insensitive)
                String[] headers = headerLine.split(",");
                Map<String, Integer> indexMap = resolveHeaders(headers);

                Integer regNoIdx = indexMap.get("regno");
                if (regNoIdx == null) {
                    storeError(jobId, "Missing required column: Regd. No or RegNo");
                    return;
                }

                String line;
                int rowNum = 1;
                while ((line = reader.readLine()) != null) {
                    rowNum++;
                    if (line.isBlank()) continue;

                    String[] cols = line.split(",", -1);
                    if (regNoIdx >= cols.length) {
                        rowErrors.add(ImportResult.RowError.builder()
                                .row(rowNum).reason("Row too short").build());
                        skipped++;
                        continue;
                    }

                    String regNo = cols[regNoIdx].trim().toLowerCase();
                    if (regNo.isEmpty()) {
                        rowErrors.add(ImportResult.RowError.builder()
                                .row(rowNum).reason("Empty Regd. No").build());
                        skipped++;
                        continue;
                    }

                    Optional<Student> studentOpt = studentRepository.findByRegNo(regNo);
                    if (studentOpt.isEmpty()) {
                        rowErrors.add(ImportResult.RowError.builder()
                                .row(rowNum).reason("Student not found: " + regNo).build());
                        skipped++;
                        continue;
                    }

                    Student student = studentOpt.get();

                    // Upsert: skip if already in roster for this room+day
                    if (!rosterRepository.existsByRoomIdAndStudentIdAndDay(
                            roomId, student.getId(), day)) {
                        RoomRoster entry = RoomRoster.builder()
                                .room(room)
                                .student(student)
                                .day(day)
                                .build();
                        rosterRepository.save(entry);
                        imported++;
                    } else {
                        skipped++; // already in roster — count as skipped (not error)
                    }
                }
            }

            ImportResult result = ImportResult.builder()
                    .imported(imported)
                    .skipped(skipped)
                    .errors(rowErrors.size())
                    .rowErrors(rowErrors)
                    .build();

            redisTemplate.opsForValue()
                    .set("upload:job:" + jobId,
                         objectMapper.writeValueAsString(result),
                         2, TimeUnit.HOURS);

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
}
