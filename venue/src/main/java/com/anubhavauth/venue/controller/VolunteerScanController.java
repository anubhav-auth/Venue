package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ScanRequest;
import com.anubhavauth.venue.entity.CheckIn;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.CheckInRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.HashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/volunteers")
@RequiredArgsConstructor
public class VolunteerScanController {

    private final StudentRepository studentRepository;
    private final CheckInRepository checkInRepository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    @PostMapping("/scan")
    @Transactional
    public ResponseEntity<?> scanVolunteer(@Valid @RequestBody ScanRequest request) {
        try {
            // Parse QR JSON
            Map<?, ?> qrMap = objectMapper.readValue(request.getQrData(), Map.class);

            String role = (String) qrMap.get("role");
            String hash = (String) qrMap.get("hash");
            Number studentIdNum = (Number) qrMap.get("studentId");

            if (studentIdNum == null || role == null || hash == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid QR format"));
            }

            // Must be a VOLUNTEER QR
            if (!"VOLUNTEER".equals(role)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Not a volunteer QR code"));
            }

            Long studentId = studentIdNum.longValue();

            // Verify hash
            if (!hashService.verifyHash(studentId + "VOLUNTEER", hash)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid QR — tampered hash"));
            }

            // Find student
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

            // Confirm they are actually a volunteer
            if (!"VOLUNTEER".equals(student.getRole())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Student is not a volunteer"));
            }

            // Determine day — use provided day or default to day1
            String day = (request.getDay() != null && !request.getDay().isBlank())
                    ? request.getDay() : "day1";

            // Check for duplicate check-in on this day
            if (checkInRepository.existsByStudentIdAndDayAndDeletedAtIsNull(studentId, day)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", "Volunteer already checked in for " + day));
            }

            // Create check-in — room and verifier are null for admin scan
            CheckIn checkIn = CheckIn.builder()
                    .student(student)
                    .room(null)
                    .seatNumber(null)
                    .verifier(null)
                    .method("adminScan")
                    .day(day)
                    .build();

            checkInRepository.save(checkIn);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "studentId", studentId,
                    "name", student.getName(),
                    "regNo", student.getRegNo(),
                    "day", day
            ));

        } catch (RuntimeException e) {
            if ("STUDENT_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Student not found"));
            }
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid QR data: " + e.getMessage()));
        }
    }
}
