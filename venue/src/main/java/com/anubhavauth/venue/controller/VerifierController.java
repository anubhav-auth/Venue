package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ScanRequest;
import com.anubhavauth.venue.dto.VerifierDashboardDto;
import com.anubhavauth.venue.entity.*;
import com.anubhavauth.venue.repository.*;
import com.anubhavauth.venue.util.HashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/verifier")
@RequiredArgsConstructor
public class VerifierController {

    private final VerifierRepository verifierRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final CheckInRepository checkInRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final StudentRepository studentRepository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getStats(
            @RequestParam String day,
            Authentication auth) {

        Verifier verifier = verifierRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        Optional<VerifierAssignment> assignmentOpt =
                verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);

        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Not assigned to " + day));
        }

        Room room = assignmentOpt.get().getRoom();
        long checkedIn = checkInRepository.countByRoomIdAndDayAndDeletedAtIsNull(room.getId(), day);
        long remaining = Math.max(0, room.getCapacity() - checkedIn);
        double percentage = room.getCapacity() > 0
                ? (checkedIn * 100.0 / room.getCapacity()) : 0.0;

        String status = percentage >= 80 ? "high" : percentage >= 50 ? "medium" : "low";

        return ResponseEntity.ok(VerifierDashboardDto.builder()
                .verifierName(verifier.getName())
                .roomId(room.getId())
                .roomName(room.getRoomName())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .day(day)
                .capacity(room.getCapacity())
                .checkedInCount(checkedIn)
                .remaining(remaining)
                .percentage(Math.round(percentage * 10.0) / 10.0)
                .status(status)
                .build());
    }

    @PostMapping("/check-in/scan")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<?> scan(
            @Valid @RequestBody ScanRequest request,
            Authentication auth) {

        if (request.getDay() == null || request.getDay().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Day is required"));
        }

        Verifier verifier = verifierRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        try {
            Map<?, ?> qrMap = objectMapper.readValue(request.getQrData(), Map.class);
            String role = (String) qrMap.get("role");
            String hash = (String) qrMap.get("hash");
            Number studentIdNum = (Number) qrMap.get("studentId");

            if (studentIdNum == null || role == null || hash == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid QR format"));
            }

            Long studentId = studentIdNum.longValue();

            if ("VOLUNTEER".equals(role)) {
                return handleVolunteerScan(studentId, hash, request.getDay(), verifier);
            } else if ("AUDIENCE".equals(role)) {
                return handleAudienceScan(qrMap, studentId, hash, request.getDay(), verifier);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Unknown QR role: " + role));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid QR: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> handleVolunteerScan(
            Long studentId, String hash, String day, Verifier verifier) {

        if (!hashService.verifyHash(studentId + "VOLUNTEER", hash)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Tampered QR hash"));
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        if (checkInRepository.existsByStudentIdAndDayAndDeletedAtIsNull(studentId, day)) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Volunteer already checked in for " + day
            ));
        }

        checkInRepository.save(CheckIn.builder()
                .student(student)
                .room(null)
                .seatNumber(null)
                .verifier(verifier)
                .method("qrscan")
                .day(day)
                .build());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentName", student.getName(),
                "seatNumber", (Object) null,
                "roomName", "N/A",
                "message", "Volunteer checked in"
        ));
    }

    private ResponseEntity<?> handleAudienceScan(
            Map<?, ?> qrMap, Long studentId, String hash, String day, Verifier verifier) {

        Number roomIdNum = (Number) qrMap.get("roomId");
        String qrDay = (String) qrMap.get("day");
        String seatNumber = (String) qrMap.get("seatNumber");

        if (roomIdNum == null || qrDay == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid audience QR format"));
        }

        Long qrRoomId = roomIdNum.longValue();

        // Verify hash
        if (!hashService.verifyHash(studentId + "" + qrRoomId, hash)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Tampered QR hash"));
        }

        // Day must match
        if (!qrDay.equals(day)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false,
                            "message", "Wrong day — ticket is for " + qrDay));
        }

        // Room must match verifier's assignment
        Optional<VerifierAssignment> assignment =
                verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);

        if (assignment.isEmpty() || !assignment.get().getRoom().getId().equals(qrRoomId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Wrong room for this verifier"));
        }

        // Duplicate check
        if (checkInRepository.existsByStudentIdAndDayAndDeletedAtIsNull(studentId, day)) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Student already checked in"
            ));
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        Room room = assignment.get().getRoom();

        checkInRepository.save(CheckIn.builder()
                .student(student)
                .room(room)
                .seatNumber(seatNumber)
                .verifier(verifier)
                .method("qrscan")
                .day(day)
                .build());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentName", student.getName(),
                "seatNumber", seatNumber != null ? seatNumber : "No Seat",
                "roomName", room.getRoomName(),
                "message", "Checked in successfully"
        ));
    }
}
