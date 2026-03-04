package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.AllocationResultDto;
import com.anubhavauth.venue.dto.AllocationSummaryDto;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
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
                return ResponseEntity.status(409)
                        .body(Map.of("error", "Allocation already exists. Use /allocate/clear first."));
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
    public ResponseEntity<?> getAllocations(
            @RequestParam(required = false) String day,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var assignments = (day != null && !day.isBlank())
                ? seatAssignmentRepository.findAll().stream()
                .filter(sa -> sa.getDay().equals(day)).toList()
                : seatAssignmentRepository.findAll();

        return ResponseEntity.ok(assignments);
    }
}
