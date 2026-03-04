package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.AllocationResultDto;
import com.anubhavauth.venue.dto.AllocationSummaryDto;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.anubhavauth.venue.dto.AllocationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


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

        List<SeatAssignment> all = seatAssignmentRepository.findAll();

        List<AllocationDto> filtered = all.stream().filter(sa -> day == null || day.isBlank() || sa.getDay().equals(day)).filter(sa -> roomId == null || roomId.isBlank() || sa.getRoom().getId().toString().equals(roomId)).map(sa -> {
            Student s = sa.getStudent();
            Room r = sa.getRoom();
            return AllocationDto.builder().assignmentId(sa.getId()).studentId(s.getId()).regNo(s.getRegNo()).name(s.getName()).lastName(s.getLastName()).degree(s.getDegree()).passoutYear(s.getPassoutYear()).role(s.getRole()).roomId(r.getId()).roomName(r.getRoomName()).building(r.getBuilding()).floor(r.getFloor()).seatNumber(sa.getSeatNumber()).day(sa.getDay()).overflow(sa.getSeatNumber() == null).build();
        }).toList();

        // Manual pagination
        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<AllocationDto> pageContent = filtered.subList(fromIndex, toIndex);

        return ResponseEntity.ok(Map.of("content", pageContent, "page", page, "size", size, "totalElements", total, "totalPages", (int) Math.ceil((double) total / size), "last", toIndex >= total));
    }

}
