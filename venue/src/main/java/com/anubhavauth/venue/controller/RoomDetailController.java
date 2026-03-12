package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.RoomDetailDto;
import com.anubhavauth.venue.entity.*;
import com.anubhavauth.venue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class RoomDetailController {

    private final RoomRepository roomRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckInRepository checkInRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;

    @GetMapping("/{id}/details")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRoomDetails(
            @PathVariable Long id,
            @RequestParam String day) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

        // Assigned verifiers for this room+day
        List<RoomDetailDto.VerifierInfo> verifiers = verifierAssignmentRepository
                .findAll().stream()
                .filter(va -> va.getRoom().getId().equals(id) && va.getDay().equals(day))
                .map(va -> RoomDetailDto.VerifierInfo.builder()
                        .verifierId(va.getVerifier().getId())
                        .username(va.getVerifier().getUsername())
                        .name(va.getVerifier().getName())
                        .build())
                .toList();

        // All seat assignments for this room+day
        List<SeatAssignment> assignments = seatAssignmentRepository.findByRoomIdAndDay(id, day);

        // Batch-load all active CheckIns for this room+day in ONE query — no N+1
        Map<Long, CheckIn> checkInByStudentId = checkInRepository
                .findByRoomIdAndDayAndDeletedAtIsNull(id, day)
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getStudent().getId(),
                        c -> c,
                        (a, b) -> a   // keep first if somehow duplicate
                ));

        List<RoomDetailDto.SeatInfo> seats = assignments.stream()
                .filter(sa -> sa.getSeatNumber() != null)       // regular seats
                .map(sa -> buildSeatInfo(sa, checkInByStudentId))
                .toList();

        List<RoomDetailDto.SeatInfo> overflow = assignments.stream()
                .filter(sa -> sa.getSeatNumber() == null)       // overflow
                .map(sa -> buildSeatInfo(sa, checkInByStudentId))
                .toList();

        return ResponseEntity.ok(RoomDetailDto.builder()
                .roomId(room.getId())
                .roomName(room.getRoomName())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .day(day)
                .capacity(room.getCapacity())
                .seatsPerRow(room.getSeatsPerRow())
                .skipRows(room.getSkipRows())
                .assignedVerifiers(verifiers)
                .seats(seats)
                .overflow(overflow)
                .build());
    }

    private RoomDetailDto.SeatInfo buildSeatInfo(SeatAssignment sa,
                                                  Map<Long, CheckIn> checkInByStudentId) {
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
                .checkInId(checkIn != null ? checkIn.getId() : null)
                .build();
    }
}
