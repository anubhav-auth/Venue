package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ScanRequest;
import com.anubhavauth.venue.dto.VerifierDashboardDto;
import com.anubhavauth.venue.entity.*;
import com.anubhavauth.venue.event.CheckInEvent;
import com.anubhavauth.venue.repository.*;
import com.anubhavauth.venue.service.CheckInEventPublisher;
import com.anubhavauth.venue.service.SeatAssignmentService;
import com.anubhavauth.venue.dto.SeatAssignResult;
import com.anubhavauth.venue.util.HashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final CheckInEventPublisher eventPublisher;
    private final SeatAssignmentService seatAssignmentService;


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

        // FIX 3: Role-based permission check
        // The scanning user's role is encoded in their Verifier record:
        //   isTeamLead=false and ROLE_VERIFIER → block entirely
        //   isTeamLead=true  and ROLE_TEAM_LEAD → block only if target is also a team lead
        //   ADMIN role does not have a Verifier record — but this method is only reachable
        //   from the /api/verifier/** endpoint (hasAnyRole VERIFIER, TEAM_LEAD, ADMIN).
        //   Admins are authenticated via a different principal; the verifierRepository lookup
        //   above in scan() would throw VERIFIER_NOT_FOUND for admins.
        //   So here, `verifier` is always a VERIFIER or TEAM_LEAD.

        if (!verifier.isTeamLead()) {
            // Regular verifier — cannot scan volunteer QR codes
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Regular verifiers cannot scan volunteer QR codes"));
        }

        // Team lead — check if the target volunteer is also a team lead
        Optional<Verifier> targetVerifierOpt = verifierRepository.findByUsername(
                studentRepository.findById(studentId)
                        .map(s -> s.getRegNo())
                        .orElse(null)
        );
        boolean targetIsTeamLead = targetVerifierOpt.map(Verifier::isTeamLead).orElse(false);
        if (targetIsTeamLead) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Team leads can only be verified by an admin"));
        }

        // Proceed with the volunteer scan
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

        eventPublisher.publish(CheckInEvent.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .regNo(student.getRegNo())
                .roomId(null)
                .roomName("N/A")
                .seatNumber(null)
                .day(day)
                .verifierUsername(verifier.getUsername())
                .checkInTime(LocalDateTime.now())
                .roomCheckedInCount(0)
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

        // Verifier must be assigned to this room (via VerifierAssignment or assignedRoom)
        Optional<VerifierAssignment> assignment =
                verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);
        boolean roomMatches = assignment.isPresent() && assignment.get().getRoom().getId().equals(qrRoomId);
        // Also allow team lead who has assignedRoom matching
        if (!roomMatches && verifier.getAssignedRoom() != null) {
            roomMatches = verifier.getAssignedRoom().getId().equals(qrRoomId);
        }
        if (!roomMatches) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Wrong room for this verifier"));
        }

        // Duplicate check-in — return existing result
        if (checkInRepository.existsByStudentIdAndDayAndDeletedAtIsNull(studentId, day)) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));
            Room room = assignment.isPresent() ? assignment.get().getRoom() : verifier.getAssignedRoom();
            Optional<String> existingSeat = seatAssignmentRepository
                    .findByStudentIdAndDay(studentId, day)
                    .map(sa -> sa.getSeatNumber());
            Optional<CheckIn> existingCheckIn = checkInRepository
                    .findByStudentIdAndDayAndDeletedAtIsNull(studentId, day);
            java.util.Map<String, Object> dupResponse = new java.util.HashMap<>();
            dupResponse.put("success", true);
            dupResponse.put("alreadyCheckedIn", true);
            dupResponse.put("studentId", studentId);
            dupResponse.put("name", student.getName());
            dupResponse.put("regNo", student.getRegNo());
            dupResponse.put("degree", student.getDegree() != null ? student.getDegree() : "");
            dupResponse.put("branch", "");
            dupResponse.put("passoutYear", student.getPassoutYear() != null ? student.getPassoutYear() : 0);
            dupResponse.put("seatNumber", existingSeat.orElse(null) != null ? existingSeat.get() : "");
            dupResponse.put("roomName", room != null ? room.getRoomName() : "");
            dupResponse.put("verifierUsername", verifier.getUsername());
            dupResponse.put("checkInId", existingCheckIn.map(CheckIn::getId).orElse(null));
            return ResponseEntity.ok(dupResponse);
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));
        Room room = assignment.isPresent() ? assignment.get().getRoom() : verifier.getAssignedRoom();

        // Assign seat on scan (includes roster check + pessimistic lock)
        String assignedSeat = null;
        try {
            SeatAssignResult seatResult = seatAssignmentService.assignSeatOnScan(qrRoomId, studentId, day);
            assignedSeat = seatResult.getSeatNumber();
        } catch (RuntimeException e) {
            if ("STUDENT_NOT_ON_ROSTER".equals(e.getMessage())) {
                return ResponseEntity.status(403)
                        .body(Map.of("success", false, "message", "Student not on room roster"));
            }
            // If roster not used, fall back gracefully (seat stays null)
        }

        CheckIn saved = checkInRepository.save(CheckIn.builder()
                .student(student)
                .room(room)
                .seatNumber(assignedSeat)
                .verifier(verifier)
                .method("qrscan")
                .day(day)
                .build());

        long roomCount = room != null
                ? checkInRepository.countByRoomIdAndDayAndDeletedAtIsNull(room.getId(), day)
                : 0;

        eventPublisher.publish(CheckInEvent.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .regNo(student.getRegNo())
                .roomId(room != null ? room.getId() : null)
                .roomName(room != null ? room.getRoomName() : "N/A")
                .seatNumber(assignedSeat)
                .day(day)
                .verifierUsername(verifier.getUsername())
                .checkInTime(saved.getCheckInTime())
                .roomCheckedInCount(roomCount)
                .build());

        java.util.Map<String, Object> freshResponse = new java.util.HashMap<>();
        freshResponse.put("success", true);
        freshResponse.put("alreadyCheckedIn", false);
        freshResponse.put("studentId", studentId);
        freshResponse.put("name", student.getName());
        freshResponse.put("regNo", student.getRegNo());
        freshResponse.put("degree", student.getDegree() != null ? student.getDegree() : "");
        freshResponse.put("branch", "");
        freshResponse.put("passoutYear", student.getPassoutYear() != null ? student.getPassoutYear() : 0);
        freshResponse.put("seatNumber", assignedSeat != null ? assignedSeat : "");
        freshResponse.put("roomName", room != null ? room.getRoomName() : "");
        freshResponse.put("verifierUsername", verifier.getUsername());
        freshResponse.put("checkInTime", saved.getCheckInTime().toString());
        freshResponse.put("checkInId", saved.getId());
        return ResponseEntity.ok(freshResponse);
    }
}
