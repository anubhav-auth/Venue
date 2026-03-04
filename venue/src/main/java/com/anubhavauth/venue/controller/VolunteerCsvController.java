package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.CsvColumnResolver;
import com.anubhavauth.venue.util.HashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@RestController
@RequestMapping("/api/admin/volunteers")
@RequiredArgsConstructor
public class VolunteerCsvController {

    private final StudentRepository studentRepository;
    private final CsvColumnResolver csvColumnResolver;
    private final BCryptPasswordEncoder passwordEncoder;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    @PostMapping("/upload")
    public ResponseEntity<ImportResult> uploadVolunteers(@RequestParam("file") MultipartFile file) {
        List<ImportResult.RowError> rowErrors = new ArrayList<>();
        List<Student> toSave = new ArrayList<>();
        int imported = 0, skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return ResponseEntity.badRequest().body(ImportResult.builder()
                        .errors(1).rowErrors(List.of(ImportResult.RowError.builder()
                                .row(0).reason("Empty file").build())).build());
            }

            Map<String, String> columnDefs = csvColumnResolver.getStudent().get("columns");
            Map<String, Integer> indexMap = csvColumnResolver.resolveHeaders(headerLine, columnDefs);

            for (String required : List.of("name", "regNo", "email", "lastName", "degree", "contactNo", "passoutYear")) {
                if (!indexMap.containsKey(required)) {
                    return ResponseEntity.badRequest().body(ImportResult.builder()
                            .errors(1).rowErrors(List.of(ImportResult.RowError.builder()
                                    .row(0).reason("Missing required column: " + required).build())).build());
                }
            }

            List<String> validDegrees = csvColumnResolver.getValidDegrees();
            Set<String> seenInBatch = new HashSet<>();

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);

                String regNo         = csvColumnResolver.getValue(cols, indexMap, "regNo");
                String name          = csvColumnResolver.getValue(cols, indexMap, "name");
                String lastName      = csvColumnResolver.getValue(cols, indexMap, "lastName");
                String email         = csvColumnResolver.getValue(cols, indexMap, "email");
                String degree        = csvColumnResolver.getValue(cols, indexMap, "degree");
                String contactNo     = csvColumnResolver.getValue(cols, indexMap, "contactNo");
                String passoutYearStr = csvColumnResolver.getValue(cols, indexMap, "passoutYear");

                if (regNo == null || name == null || lastName == null || email == null
                        || degree == null || contactNo == null || passoutYearStr == null) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Missing required field(s)").build());
                    skipped++;
                    continue;
                }

                if (!validDegrees.contains(degree.toUpperCase())) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Invalid degree: " + degree
                                    + ". Must be one of: " + validDegrees).build());
                    skipped++;
                    continue;
                }

                if (studentRepository.existsByRegNo(regNo) || seenInBatch.contains(regNo)) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Duplicate regNo: " + regNo + " — skipped").build());
                    skipped++;
                    continue;
                }

                int passoutYear;
                try {
                    passoutYear = Integer.parseInt(passoutYearStr.trim());
                } catch (NumberFormatException e) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Invalid passout year: " + passoutYearStr).build());
                    skipped++;
                    continue;
                }

                String rawPassword = regNo.toLowerCase() + lastName.toLowerCase();
                String passwordHash = passwordEncoder.encode(rawPassword);

                Student volunteer = Student.builder()
                        .regNo(regNo)
                        .name(name)
                        .lastName(lastName)
                        .email(email)
                        .degree(degree.toUpperCase())
                        .contactNo(contactNo)
                        .passoutYear(passoutYear)
                        .passwordHash(passwordHash)
                        .role("VOLUNTEER")
                        .isPromoted(false)
                        .build();

                toSave.add(volunteer);
                seenInBatch.add(regNo);
                imported++;
            }

            // Save first to get generated IDs, then set QR codes
            List<Student> saved = studentRepository.saveAll(toSave);

            for (Student vol : saved) {
                String hash = hashService.generateHash(vol.getId() + "VOLUNTEER");
                Map<String, Object> qrPayload = Map.of(
                        "studentId", vol.getId(),
                        "role", "VOLUNTEER",
                        "hash", hash
                );
                vol.setQrCodeData(objectMapper.writeValueAsString(qrPayload));
            }
            studentRepository.saveAll(saved);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ImportResult.builder()
                    .errors(1).rowErrors(List.of(ImportResult.RowError.builder()
                            .row(0).reason("Failed to parse file: " + e.getMessage()).build())).build());
        }

        return ResponseEntity.ok(ImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(rowErrors.size())
                .rowErrors(rowErrors)
                .build());
    }
}
