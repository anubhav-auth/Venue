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
        if (!hashService.verifyHash(studentId, "VOLUNTEER", hash)) {
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
        String qrDay     = (String)  qrMap.get("day");

        // ── Determine the room ────────────────────────────────────────────────
        // New-style QR (no roomId/day embedded): use verifier's assigned room.
        // Legacy QR (roomId + day embedded):     validate as before.
        Room room;
        if (roomIdNum == null || qrDay == null) {
            // No room in QR → derive from verifier assignment for this day
            Optional<VerifierAssignment> assignment =
                    verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);

            if (assignment.isPresent()) {
                room = assignment.get().getRoom();
            } else if (verifier.getAssignedRoom() != null) {
                room = verifier.getAssignedRoom();          // team-lead fallback
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", "Verifier not assigned to a room for " + day));
            }

            // Hash was generated without a roomId — verify as studentId + "AUDIENCE"
            if (!hashService.verifyHash(studentId, "AUDIENCE", hash)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Tampered QR hash"));
            }

        } else {
            // Legacy QR with roomId + day baked in
            Long qrRoomId = roomIdNum.longValue();

            if (!qrDay.equals(day)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", "Wrong day — ticket is for " + qrDay));
            }
            if (!hashService.verifyHash(studentId, qrRoomId, hash)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Tampered QR hash"));
            }

            // Verifier must be assigned to the room encoded in the QR
            Optional<VerifierAssignment> assignment =
                    verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);
            boolean roomMatches = assignment.isPresent()
                    && assignment.get().getRoom().getId().equals(qrRoomId);
            if (!roomMatches && verifier.getAssignedRoom() != null) {
                roomMatches = verifier.getAssignedRoom().getId().equals(qrRoomId);
            }
            if (!roomMatches) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Wrong room for this verifier"));
            }
            room = assignment.isPresent() ? assignment.get().getRoom() : verifier.getAssignedRoom();
        }

        // ── Duplicate check ───────────────────────────────────────────────────
        if (checkInRepository.existsByStudentIdAndDayAndDeletedAtIsNull(studentId, day)) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));
            Optional<String> existingSeat = seatAssignmentRepository
                    .findByStudentIdAndDay(studentId, day)
                    .map(SeatAssignment::getSeatNumber);
            Optional<CheckIn> existingCheckIn = checkInRepository
                    .findByStudentIdAndDayAndDeletedAtIsNull(studentId, day);

            Map<String, Object> dup = new java.util.HashMap<>();
            dup.put("success",          true);
            dup.put("alreadyCheckedIn", true);
            dup.put("studentId",        studentId);
            dup.put("name",             student.getName());
            dup.put("regNo",            student.getRegNo());
            dup.put("degree",           student.getDegree() != null ? student.getDegree() : "");
            dup.put("passoutYear",      student.getPassoutYear() != null ? student.getPassoutYear() : 0);
            dup.put("seatNumber",       existingSeat.orElse(null));
            dup.put("roomName",         room.getRoomName());
            dup.put("verifierUsername", verifier.getUsername());
            dup.put("checkInId",        existingCheckIn.map(CheckIn::getId).orElse(null));
            return ResponseEntity.ok(dup);
        }

        // ── Fresh check-in ────────────────────────────────────────────────────
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        String assignedSeat = null;
        try {
            SeatAssignResult seatResult =
                    seatAssignmentService.assignSeatOnScan(room.getId(), studentId, day);
            assignedSeat = seatResult.getSeatNumber();
        } catch (RuntimeException e) {
            if ("STUDENT_NOT_ON_ROSTER".equals(e.getMessage())) {
                return ResponseEntity.status(403)
                        .body(Map.of("success", false, "message", "Student not on room roster"));
            }
            // roster not used — seat stays null (overflow)
        }

        CheckIn saved = checkInRepository.save(CheckIn.builder()
                .student(student)
                .room(room)
                .seatNumber(assignedSeat)
                .verifier(verifier)
                .method("qr_scan")
                .day(day)
                .build());

        long roomCount = checkInRepository
                .countByRoomIdAndDayAndDeletedAtIsNull(room.getId(), day);

        eventPublisher.publish(CheckInEvent.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .regNo(student.getRegNo())
                .roomId(room.getId())
                .roomName(room.getRoomName())
                .seatNumber(assignedSeat)
                .day(day)
                .verifierUsername(verifier.getUsername())
                .checkInTime(saved.getCheckInTime())
                .roomCheckedInCount(roomCount)
                .build());

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success",          true);
        resp.put("alreadyCheckedIn", false);
        resp.put("studentId",        studentId);
        resp.put("name",             student.getName());
        resp.put("regNo",            student.getRegNo());
        resp.put("degree",           student.getDegree() != null ? student.getDegree() : "");
        resp.put("passoutYear",      student.getPassoutYear() != null ? student.getPassoutYear() : 0);
        resp.put("seatNumber",       assignedSeat != null ? assignedSeat : "");
        resp.put("roomName",         room.getRoomName());
        resp.put("verifierUsername", verifier.getUsername());
        resp.put("checkInTime",      saved.getCheckInTime().toString());
        resp.put("checkInId",        saved.getId());
        return ResponseEntity.ok(resp);
    }

}
