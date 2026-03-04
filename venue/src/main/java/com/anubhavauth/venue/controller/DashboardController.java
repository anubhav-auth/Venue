package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.DashboardStatsDto;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.repository.CheckInRepository;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final RoomRepository roomRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckInRepository checkInRepository;

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<DashboardStatsDto> getStats(
            @RequestParam(required = false) String day) {

        List<Room> rooms = (day != null && !day.isBlank())
                ? roomRepository.findByDay(day)
                : roomRepository.findAll();

        long totalStudents = 0;
        long totalCheckedIn = 0;

        List<DashboardStatsDto.RoomStats> roomStats = rooms.stream().map(room -> {
            // Count assigned students (seat assignments) for this room
            int assigned = seatAssignmentRepository
                    .findByRoomIdAndDay(room.getId(), room.getDay()).size();

            // Count active check-ins for this room
            long checkedIn = checkInRepository
                    .countByRoomIdAndDayAndDeletedAtIsNull(room.getId(), room.getDay());

            double pct = assigned > 0 ? (checkedIn * 100.0 / assigned) : 0.0;
            pct = Math.round(pct * 10.0) / 10.0;

            String status = pct >= 80 ? "high" : pct >= 50 ? "medium" : "low";

            return DashboardStatsDto.RoomStats.builder()
                    .roomId(room.getId())
                    .roomName(room.getRoomName())
                    .building(room.getBuilding())
                    .floor(room.getFloor())
                    .capacity(assigned)   // effective capacity = assigned students
                    .checkedIn(checkedIn)
                    .percentage(pct)
                    .status(status)
                    .build();
        }).toList();

        // Aggregate totals
        long grandTotal = roomStats.stream().mapToLong(DashboardStatsDto.RoomStats::getCapacity).sum();
        long grandCheckedIn = roomStats.stream().mapToLong(DashboardStatsDto.RoomStats::getCheckedIn).sum();
        double overallPct = grandTotal > 0
                ? Math.round((grandCheckedIn * 100.0 / grandTotal) * 10.0) / 10.0 : 0.0;

        // Sort by percentage ascending — problem rooms first
        List<DashboardStatsDto.RoomStats> sorted = roomStats.stream()
                .sorted(Comparator.comparingDouble(DashboardStatsDto.RoomStats::getPercentage))
                .toList();

        return ResponseEntity.ok(DashboardStatsDto.builder()
                .overall(DashboardStatsDto.OverallStats.builder()
                        .totalStudents(grandTotal)
                        .checkedIn(grandCheckedIn)
                        .percentage(overallPct)
                        .notCheckedIn(grandTotal - grandCheckedIn)
                        .build())
                .rooms(sorted)
                .lastUpdated(LocalDateTime.now())
                .build());
    }
}
