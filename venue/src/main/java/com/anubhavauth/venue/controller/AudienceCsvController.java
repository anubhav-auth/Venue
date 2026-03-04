package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.CsvColumnResolver;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audience")
@RequiredArgsConstructor
public class AudienceCsvController {

    private final StudentRepository studentRepository;
    private final CsvColumnResolver csvColumnResolver;
    private final BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/upload")
    public ResponseEntity<ImportResult> uploadAudience(@RequestParam("file") MultipartFile file) {
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

            // Validate all required columns exist
            for (String required : List.of("name", "regNo", "email", "lastName", "degree", "contactNo", "passoutYear")) {
                if (!indexMap.containsKey(required)) {
                    return ResponseEntity.badRequest().body(ImportResult.builder()
                            .errors(1).rowErrors(List.of(ImportResult.RowError.builder()
                                    .row(0).reason("Missing required column: " + required).build())).build());
                }
            }

            List<String> validDegrees = csvColumnResolver.getValidDegrees();

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);

                String regNo      = csvColumnResolver.getValue(cols, indexMap, "regNo");
                String name       = csvColumnResolver.getValue(cols, indexMap, "name");
                String lastName   = csvColumnResolver.getValue(cols, indexMap, "lastName");
                String email      = csvColumnResolver.getValue(cols, indexMap, "email");
                String degree     = csvColumnResolver.getValue(cols, indexMap, "degree");
                String contactNo  = csvColumnResolver.getValue(cols, indexMap, "contactNo");
                String passoutYearStr = csvColumnResolver.getValue(cols, indexMap, "passoutYear");

                // Validate required fields
                if (regNo == null || name == null || lastName == null || email == null
                        || degree == null || contactNo == null || passoutYearStr == null) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Missing required field(s)").build());
                    skipped++;
                    continue;
                }

                // Hard reject invalid degree
                if (!validDegrees.contains(degree.toUpperCase())) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Invalid degree: " + degree
                                    + ". Must be one of: " + validDegrees).build());
                    skipped++;
                    continue;
                }

                // Skip duplicate regNo
                if (studentRepository.existsByRegNo(regNo)) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Duplicate regNo: " + regNo + " — skipped").build());
                    skipped++;
                    continue;
                }

                // Parse passout year
                int passoutYear;
                try {
                    passoutYear = Integer.parseInt(passoutYearStr.trim());
                } catch (NumberFormatException e) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Invalid passout year: " + passoutYearStr).build());
                    skipped++;
                    continue;
                }

                // Password = regNo + lastName (both lowercased)
                String rawPassword = regNo.toLowerCase() + lastName.toLowerCase();
                String passwordHash = passwordEncoder.encode(rawPassword);

                toSave.add(Student.builder()
                        .regNo(regNo)
                        .name(name)
                        .lastName(lastName)
                        .email(email)
                        .degree(degree.toUpperCase())
                        .contactNo(contactNo)
                        .passoutYear(passoutYear)
                        .passwordHash(passwordHash)
                        .role("AUDIENCE")
                        .isPromoted(false)
                        .build());
                imported++;
            }

            studentRepository.saveAll(toSave);

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
