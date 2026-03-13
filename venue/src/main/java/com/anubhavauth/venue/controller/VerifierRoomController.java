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

@RestController
@RequestMapping("/api/verifier")
@RequiredArgsConstructor
public class VerifierRoomController {

    private final VerifierRepository verifierRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckInRepository checkInRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final RoomRosterRepository roomRosterRepository;

    @GetMapping("my-room")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyRoom(Authentication auth,
                                       @RequestParam(required = false) String day) {
        Verifier verifier = verifierRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("VERIFIERNOTFOUND"));

        // ── resolve room by day assignment first, fall back to assignedRoom ──
        Room room;
        String resolvedDay;

        if (day != null && !day.isBlank()) {
            Optional<VerifierAssignment> dayAssignment =
                    verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);
            if (dayAssignment.isPresent()) {
                room = dayAssignment.get().getRoom();
            } else {
                room = verifier.getAssignedRoom();
            }
            resolvedDay = day;
        } else {
            room = verifier.getAssignedRoom();
            resolvedDay = (room != null) ? room.getDay() : "day1";
        }

        if (room == null) return ResponseEntity.noContent().build();

        List<RoomDetailDto.VerifierInfo> verifiers = verifierAssignmentRepository
                .findAll().stream()
                .filter(va -> va.getRoom().getId().equals(room.getId())
                        && va.getDay().equals(resolvedDay))
                .map(va -> RoomDetailDto.VerifierInfo.builder()
                        .verifierId(va.getVerifier().getId())
                        .username(va.getVerifier().getUsername())
                        .name(va.getVerifier().getName())
                        .build())
                .toList();

        List<SeatAssignment> assignments = seatAssignmentRepository
                .findByRoomIdAndDay(room.getId(), resolvedDay);

        Map<Long, CheckIn> checkInByStudentId =
                checkInRepository
                        .findByRoomIdAndDayAndDeletedAtIsNull(room.getId(), resolvedDay)
                        .stream()
                        .collect(Collectors.toMap(
                                c -> c.getStudent().getId(),
                                c -> c,
                                (a, b) -> a
                        ));

        List<RoomDetailDto.SeatInfo> seats = assignments.stream()
                .filter(sa -> sa.getSeatNumber() != null)
                .map(sa -> buildSeatInfo(sa, checkInByStudentId))
                .toList();

        List<RoomDetailDto.SeatInfo> overflow = assignments.stream()
                .filter(sa -> sa.getSeatNumber() == null)
                .map(sa -> buildSeatInfo(sa, checkInByStudentId))
                .toList();

        return ResponseEntity.ok(RoomDetailDto.builder()
                .roomId(room.getId())
                .roomName(room.getRoomName())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .day(resolvedDay)
                .capacity(room.getCapacity())
                .seatsPerRow(room.getSeatsPerRow())
                .skipRows(room.getSkipRows())
                .assignedVerifiers(verifiers)
                .seats(seats)
                .overflow(overflow)
                .build());
    }

    @GetMapping("/volunteers")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getVolunteers(@RequestParam String day, Authentication auth) {
        Verifier verifier = verifierRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        Room room;
        String resolvedDay;

        if (day != null && !day.isBlank()) {
            Optional<VerifierAssignment> dayAssignment =
                    verifierAssignmentRepository.findByVerifierIdAndDay(verifier.getId(), day);
            if (dayAssignment.isPresent()) {
                room = dayAssignment.get().getRoom();
            } else {
                room = verifier.getAssignedRoom();
            }
            resolvedDay = day;
        } else {
            room = verifier.getAssignedRoom();
            resolvedDay = (room != null) ? room.getDay() : "day1";
        }

        if (room == null) return ResponseEntity.ok(java.util.Collections.emptyList());

        List<RoomRoster> roster = roomRosterRepository.findByRoomIdAndDay(room.getId(), resolvedDay);
        if (roster.isEmpty()) return ResponseEntity.ok(java.util.Collections.emptyList());

        Map<Long, CheckIn> checkInByStudentId =
                checkInRepository
                        .findByRoomIdAndDayAndDeletedAtIsNull(room.getId(), resolvedDay)
                        .stream()
                        .collect(Collectors.toMap(
                                c -> c.getStudent().getId(),
                                c -> c,
                                (a, b) -> a
                        ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (RoomRoster entry : roster) {
            Student s = entry.getStudent();
            String role = s.getRole();
            if (!"VOLUNTEER".equals(role) && !"VERIFIER".equals(role)) continue;

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
                        ? (checkIn.getVerifier() != null
                        ? checkIn.getVerifier().getUsername() : "admin")
                        : null)
                .checkInId(checkIn != null ? checkIn.getId() : null)
                .build();
    }
}
