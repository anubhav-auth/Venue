package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.entity.Verifier;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.repository.VerifierAssignmentRepository;
import com.anubhavauth.venue.repository.VerifierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.anubhavauth.venue.dto.PromoteRequest;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.VerifierAssignment;


@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class VerifierManagementController {

    private final VerifierRepository verifierRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;

    @GetMapping("/verifiers")
    @Transactional(readOnly = true)          // ← add this
    public ResponseEntity<?> listVerifiers() {
        List<Verifier> verifiers = verifierRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Verifier v : verifiers) {
            List<Map<String, Object>> assignments = new ArrayList<>();

            for (VerifierAssignment va : verifierAssignmentRepository.findByVerifierId(v.getId())) {
                Map<String, Object> a = new HashMap<>();
                a.put("day", va.getDay());
                a.put("roomId", va.getRoom().getId());        // now works — session still open
                a.put("roomName", va.getRoom().getRoomName());
                assignments.add(a);
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", v.getId());
            dto.put("username", v.getUsername());
            dto.put("name", v.getName());
            dto.put("assignments", assignments);
            result.add(dto);
        }

        return ResponseEntity.ok(result);
    }


    @PostMapping("/verifiers/{id}/demote")
    @Transactional
    public ResponseEntity<?> demoteVerifier(@PathVariable Long id) {
        Verifier verifier = verifierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        String regNo = verifier.getUsername(); // username = regNo

        // Re-enable student login
        Student student = studentRepository.findByRegNo(regNo).orElse(null);
        if (student != null) {
            student.setIsPromoted(false);
            studentRepository.save(student);
        }

        // Delete verifier — ON DELETE CASCADE removes all verifier_assignments
        verifierRepository.delete(verifier);

        return ResponseEntity.ok(Map.of(
                "message", "Verifier demoted successfully",
                "regNo", regNo
        ));
    }

    @PostMapping("/days/{day}/close")
    @Transactional
    public ResponseEntity<?> closeDay(@PathVariable String day) {
        if (!day.equals("day1") && !day.equals("day2")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Day must be 'day1' or 'day2'"));
        }

        // Find verifiers assigned ONLY to this day (no other day assignments)
        List<Long> verifierIds = verifierAssignmentRepository.findVerifierIdsWithOnlyDay(day);

        List<String> autodemotedNames = new ArrayList<>();

        for (Long verifierId : verifierIds) {
            Verifier verifier = verifierRepository.findById(verifierId).orElse(null);
            if (verifier == null) continue;

            autodemotedNames.add(verifier.getName() + " (" + verifier.getUsername() + ")");

            // Re-enable student login
            Student student = studentRepository.findByRegNo(verifier.getUsername()).orElse(null);
            if (student != null) {
                student.setIsPromoted(false);
                studentRepository.save(student);
            }

            // Delete verifier — cascade removes assignments
            verifierRepository.delete(verifier);
        }

        return ResponseEntity.ok(Map.of(
                "dayClosed", day,
                "autodemotedCount", autodemotedNames.size(),
                "autodemoted", autodemotedNames
        ));
    }

    @PutMapping("/verifiers/{id}/assignments/{day}")
    @Transactional
    public ResponseEntity<?> updateAssignment(
            @PathVariable Long id,
            @PathVariable String day,
            @RequestBody PromoteRequest.Assignment assignment) {

        Verifier verifier = verifierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        Room room = roomRepository.findById(assignment.getRoomId())
                .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

        if (!room.getDay().equals(day)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Room is not assigned to " + day));
        }

        // Delete existing assignment for this day, replace with new room
        verifierAssignmentRepository.deleteByVerifierIdAndDay(id, day);

        VerifierAssignment va = VerifierAssignment.builder()
                .verifier(verifier)
                .room(room)
                .day(day)
                .build();
        verifierAssignmentRepository.save(va);

        return ResponseEntity.ok(Map.of(
                "message", "Assignment updated",
                "verifierId", id,
                "day", day,
                "roomId", room.getId(),
                "roomName", room.getRoomName()
        ));
    }

    @DeleteMapping("/verifiers/{id}/assignments/{day}")
    @Transactional
    public ResponseEntity<?> deleteAssignment(
            @PathVariable Long id,
            @PathVariable String day) {

        Verifier verifier = verifierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("VERIFIER_NOT_FOUND"));

        verifierAssignmentRepository.deleteByVerifierIdAndDay(id, day);

        long remaining = verifierAssignmentRepository.countByVerifierId(id);

        // Auto-demote if no assignments remain
        if (remaining == 0) {
            Student student = studentRepository.findByRegNo(verifier.getUsername()).orElse(null);
            if (student != null) {
                student.setIsPromoted(false);
                studentRepository.save(student);
            }
            verifierRepository.delete(verifier);
            return ResponseEntity.ok(Map.of(
                    "message", "Last assignment removed — verifier auto-demoted",
                    "regNo", verifier.getUsername()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Assignment removed",
                "remainingAssignments", remaining
        ));
    }

}
