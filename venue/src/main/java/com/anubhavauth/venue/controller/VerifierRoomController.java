package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.RoomDetailDto;
import com.anubhavauth.venue.entity.*;
import com.anubhavauth.venue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Allows a TEAM_LEAD verifier to view the room they are assigned to manage.
 * Returns the same RoomDetailDto used in the admin RoomDetailController.
 */
@RestController
@RequestMapping("/api/verifier")
@RequiredArgsConstructor
public class VerifierRoomController {

    private final VerifierRepository verifierRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckInRepository checkInRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final RoomRosterRepository roomRosterRepository;

    // ── Fix 4: N+1 fixed, skipRows added, checkInId added ──────────────────
    @GetMapping("/my-room")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyRoom(Authentication auth) {
        Verifier verifier = verifierRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        Room room = verifier.getAssignedRoom();
        if (room == null) {
            return ResponseEntity.noContent().build(); // 204
        }

        String day = room.getDay();

        List<RoomDetailDto.VerifierInfo> verifiers = verifierAssignmentRepository
                .findAll().stream()
                .filter(va -> va.getRoom().getId().equals(room.getId()) && va.getDay().equals(day))
                .map(va -> RoomDetailDto.VerifierInfo.builder()
                        .verifierId(va.getVerifier().getId())
                        .username(va.getVerifier().getUsername())
                        .name(va.getVerifier().getName())
                        .build())
                .toList();

        List<SeatAssignment> assignments = seatAssignmentRepository
                .findByRoomIdAndDay(room.getId(), day);

        // Fix 4a — single batched check-in query (no more N+1)
        Map<Long, CheckIn> checkInByStudentId =
                checkInRepository
                        .findByRoomIdAndDayAndDeletedAtIsNull(room.getId(), day)
                        .stream()
                        .collect(Collectors.toMap(
                                c -> c.getStudent().getId(),
                                c -> c,
                                (a, b) -> a   // keep first on duplicate
                        ));

        List<RoomDetailDto.SeatInfo> seats = assignments.stream()
                .filter(sa -> sa.getSeatNumber() != null)
                .map(sa -> buildSeatInfo(sa, checkInByStudentId))
                .toList();

        List<RoomDetailDto.SeatInfo> overflow = assignments.stream()
                .filter(sa -> sa.getSeatNumber() == null)
                .map(sa -> buildSeatInfo(sa, checkInByStudentId))
                .toList();

        RoomDetailDto dto = RoomDetailDto.builder()
                .roomId(room.getId())
                .roomName(room.getRoomName())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .day(day)
                .capacity(room.getCapacity())
                .seatsPerRow(room.getSeatsPerRow())
                .skipRows(room.getSkipRows())       // Fix 4d — skipRows
                .assignedVerifiers(verifiers)
                .seats(seats)
                .overflow(overflow)
                .build();

        return ResponseEntity.ok(dto);
    }

    // ── Fix 2: GET /api/verifier/volunteers?day=day1 ─────────────────────────
    @GetMapping("/volunteers")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getVolunteers(
            @RequestParam String day,
            Authentication auth) {

        Verifier verifier = verifierRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        Room room = verifier.getAssignedRoom();
        if (room == null) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

        // Fetch all roster entries for this room+day
        List<RoomRoster> roster = roomRosterRepository.findByRoomIdAndDay(room.getId(), day);
        if (roster.isEmpty()) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

        // Collect student IDs on this roster
        List<Long> studentIds = roster.stream()
                .map(r -> r.getStudent().getId())
                .toList();

        // Batch-load check-ins for the room+day (one query)
        Map<Long, CheckIn> checkInByStudentId =
                checkInRepository
                        .findByRoomIdAndDayAndDeletedAtIsNull(room.getId(), day)
                        .stream()
                        .collect(Collectors.toMap(
                                c -> c.getStudent().getId(),
                                c -> c,
                                (a, b) -> a
                        ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (RoomRoster entry : roster) {
            Student s = entry.getStudent();
            // Include VOLUNTEER and VERIFIER roles (promoted volunteers)
            String role = s.getRole();
            if (!"VOLUNTEER".equals(role) && !"VERIFIER".equals(role)) {
                continue;
            }
            CheckIn ci = checkInByStudentId.get(s.getId());
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("studentId",   s.getId());
            row.put("name",        s.getName());
            row.put("regNo",       s.getRegNo());
            row.put("checkedIn",   ci != null);
            row.put("checkInTime", ci != null && ci.getCheckInTime() != null
                    ? ci.getCheckInTime().toString() : null);
            result.add(row);
        }

        return ResponseEntity.ok(result);
    }

    // Fix 4b/4c — uses pre-loaded map; sets checkInId
    private RoomDetailDto.SeatInfo buildSeatInfo(
            SeatAssignment sa, Map<Long, CheckIn> checkInByStudentId) {
        Student student = sa.getStudent();
        CheckIn checkIn = checkInByStudentId.get(student.getId());
        return RoomDetailDto.SeatInfo.builder()
                .studentId(student.getId())
                .name(student.getName())
                .regNo(student.getRegNo())
                .degree(student.getDegree())
                .passoutYear(student.getPassoutYear())
                .seatNumber(sa.getSeatNumber())
                .checkedIn(checkIn != null)
                .checkInTime(checkIn != null ? checkIn.getCheckInTime() : null)
                .verifierUsername(checkIn != null
                        ? (checkIn.getVerifier() != null ? checkIn.getVerifier().getUsername() : "admin")
                        : null)
                .checkInId(checkIn != null ? checkIn.getId() : null)  // Fix 4c
                .build();
    }
}
