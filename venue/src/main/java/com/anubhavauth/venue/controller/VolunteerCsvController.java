package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.service.CsvImportService;
import com.anubhavauth.venue.util.CsvColumnResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/volunteers")
@RequiredArgsConstructor
public class VolunteerCsvController {

    private final StudentRepository studentRepository;
    private final CsvColumnResolver csvColumnResolver;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CsvImportService csvImportService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("uploadExecutor")
    private Executor uploadExecutor;

    private record ParsedRow(String regNo, String name, String email,
                             String degree, String contactNo, int passoutYear) {}

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadVolunteers(
            @RequestParam("file") MultipartFile file) throws Exception {

        String jobId = UUID.randomUUID().toString();
        byte[] bytes = file.getBytes();

        stringRedisTemplate.opsForValue()
                .set("upload:job:" + jobId, "PROCESSING", 2, TimeUnit.HOURS);

        CompletableFuture.runAsync(() -> processUpload(jobId, bytes), uploadExecutor);

        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/upload/status/{jobId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable String jobId) {
        String val = stringRedisTemplate.opsForValue().get("upload:job:" + jobId);
        if (val == null) return ResponseEntity.notFound().build();
        if ("PROCESSING".equals(val)) return ResponseEntity.ok(Map.of("status", "PROCESSING"));
        if (val.startsWith("ERROR:"))
            return ResponseEntity.ok(Map.of("status", "ERROR", "message", val.substring(6)));
        try {
            ImportResult result = objectMapper.readValue(val, ImportResult.class);
            return ResponseEntity.ok(Map.of("status", "DONE", "result", result));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "ERROR", "message", "Failed to read result"));
        }
    }

    private void processUpload(String jobId, byte[] bytes) {
        List<ImportResult.RowError> rowErrors = new ArrayList<>();
        List<ParsedRow> parsedRows = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes)))) {

            String headerLine = reader.readLine();
            if (headerLine == null) { storeError(jobId, "Empty file"); return; }

            Map<String, String> columnDefs = csvColumnResolver.getStudent().get("columns");
            Map<String, Integer> indexMap = csvColumnResolver.resolveHeaders(headerLine, columnDefs);

            for (String required : List.of("name", "regNo", "email", "degree", "contactNo", "passoutYear")) {
                if (!indexMap.containsKey(required)) {
                    storeError(jobId, "Missing required column: " + required);
                    return;
                }
            }

            List<String> validDegrees = csvColumnResolver.getValidDegrees();
            Set<String> existingRegNos = studentRepository.findAllRegNos();
            Set<String> seenInBatch = new HashSet<>();

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);

                String regNo          = csvColumnResolver.getValue(cols, indexMap, "regNo");
                String name           = csvColumnResolver.getValue(cols, indexMap, "name");
                String email          = csvColumnResolver.getValue(cols, indexMap, "email");
                String degree         = csvColumnResolver.getValue(cols, indexMap, "degree");
                String contactNo      = csvColumnResolver.getValue(cols, indexMap, "contactNo");
                String passoutYearStr = csvColumnResolver.getValue(cols, indexMap, "passoutYear");

                if (regNo == null || name == null || email == null
                        || degree == null || contactNo == null || passoutYearStr == null) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum).reason("Missing required field(s)").build());
                    skipped++; continue;
                }
                if (!validDegrees.contains(degree.toUpperCase())) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                            .reason("Invalid degree: " + degree).build());
                    skipped++; continue;
                }

                regNo = regNo.toLowerCase();
                if (existingRegNos.contains(regNo) || seenInBatch.contains(regNo)) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                            .reason("Duplicate regNo: " + regNo).build());
                    skipped++; continue;
                }

                int passoutYear;
                try { passoutYear = Integer.parseInt(passoutYearStr.trim()); }
                catch (NumberFormatException e) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                            .reason("Invalid passout year: " + passoutYearStr).build());
                    skipped++; continue;
                }

                parsedRows.add(new ParsedRow(regNo, name, email, degree.toUpperCase(), contactNo, passoutYear));
                seenInBatch.add(regNo);
            }

            List<Student> toSave = parsedRows.parallelStream().map(r ->
                    Student.builder()
                            .regNo(r.regNo())
                            .name(r.name())
                            .email(r.email())
                            .degree(r.degree())
                            .contactNo(r.contactNo())
                            .passoutYear(r.passoutYear())
                            .passwordHash(passwordEncoder.encode(r.regNo()))
                            .role("VOLUNTEER")
                            .isPromoted(false)
                            .build()
            ).collect(Collectors.toList());

            csvImportService.saveVolunteers(toSave);  // handles 2-step QR internally

            ImportResult result = ImportResult.builder()
                    .imported(parsedRows.size())
                    .skipped(skipped)
                    .errors(rowErrors.size())
                    .rowErrors(rowErrors)
                    .build();

            stringRedisTemplate.opsForValue()
                    .set("upload:job:" + jobId, objectMapper.writeValueAsString(result), 2, TimeUnit.HOURS);

        } catch (Exception e) {
            storeError(jobId, e.getMessage());
        }
    }

    private void storeError(String jobId, String msg) {
        stringRedisTemplate.opsForValue()
                .set("upload:job:" + jobId, "ERROR:" + msg, 2, TimeUnit.HOURS);
    }
}
