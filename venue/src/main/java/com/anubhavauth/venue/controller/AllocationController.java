package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.AllocationResultDto;
import com.anubhavauth.venue.dto.AllocationSummaryDto;
import com.anubhavauth.venue.dto.AllocationDto;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;
    private final SeatAssignmentRepository seatAssignmentRepository;

    @PostMapping("/allocate")
    public ResponseEntity<?> allocate() {
        try {
            AllocationResultDto result = allocationService.allocateAll();
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            if ("ALREADY_ALLOCATED".equals(e.getMessage())) {
                return ResponseEntity.status(409).body(Map.of("error", "Allocation already exists. Use /allocate/clear first."));
            }
            throw e;
        }
    }

    @PostMapping("/allocate/clear")
    public ResponseEntity<?> clearAndReallocate() {
        allocationService.clearAllocations();
        AllocationResultDto result = allocationService.allocateAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/allocations/summary")
    public ResponseEntity<List<AllocationSummaryDto>> getSummary() {
        return ResponseEntity.ok(allocationService.getSummary());
    }

    @GetMapping("/allocations")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllocations(@RequestParam(required = false) String day, @RequestParam(required = false) String roomId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {

        // Cap page size to prevent OOM on large datasets
        size = Math.min(size, 200);

        // Normalize filters — pass null (not empty string) for unused filters
        String dayFilter  = (day    != null && !day.isBlank())    ? day    : null;
        Long   roomFilter = (roomId != null && !roomId.isBlank()) ?
                                Long.parseLong(roomId) : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<SeatAssignment> result =
                seatAssignmentRepository.findWithFilters(dayFilter, roomFilter, pageable);

        Page<AllocationDto> dtoPage = result.map(sa -> {
            Student s = sa.getStudent();
            Room    r = sa.getRoom();
            return AllocationDto.builder()
                    .assignmentId(sa.getId())
                    .studentId(s.getId())
                    .regNo(s.getRegNo())
                    .name(s.getName())
                    .degree(s.getDegree())
                    .passoutYear(s.getPassoutYear())
                    .role(s.getRole())
                    .roomId(r.getId())
                    .roomName(r.getRoomName())
                    .building(r.getBuilding())
                    .floor(r.getFloor())
                    .seatNumber(sa.getSeatNumber())
                    .day(sa.getDay())
                    .overflow(sa.getSeatNumber() == null)
                    .build();
        });

        return ResponseEntity.ok(dtoPage);
    }

}
