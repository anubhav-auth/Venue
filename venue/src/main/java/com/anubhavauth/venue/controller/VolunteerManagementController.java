package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.PromoteRequest;
import com.anubhavauth.venue.dto.VolunteerDto;
import com.anubhavauth.venue.entity.*;
import com.anubhavauth.venue.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class VolunteerManagementController {

    private final StudentRepository studentRepository;
    private final CheckInRepository checkInRepository;
    private final VerifierRepository verifierRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final RoomRepository roomRepository;

    @GetMapping("/volunteers")
    @Transactional(readOnly = true)
    public ResponseEntity<List<VolunteerDto>> listVolunteers() {
        List<Student> volunteers = studentRepository.findByRole("VOLUNTEER");

        List<VolunteerDto> result = volunteers.stream().map(v -> {
            boolean day1 = checkInRepository
                    .existsByStudentIdAndDayAndDeletedAtIsNull(v.getId(), "day1");
            boolean day2 = checkInRepository
                    .existsByStudentIdAndDayAndDeletedAtIsNull(v.getId(), "day2");
            boolean canPromote = !Boolean.TRUE.equals(v.getIsPromoted()) && (day1 || day2);

            return VolunteerDto.builder()
                    .studentId(v.getId())
                    .regNo(v.getRegNo())
                    .name(v.getName())
                    .email(v.getEmail())
                    .day1Attended(day1)
                    .day2Attended(day2)
                    .isPromoted(v.getIsPromoted())
                    .canPromote(canPromote)
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/volunteers/{id}/promote")
    @Transactional
    public ResponseEntity<?> promoteVolunteer(
            @PathVariable Long id,
            @Valid @RequestBody PromoteRequest request) {

        // Validate student exists and is a volunteer
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        if (!"VOLUNTEER".equals(student.getRole())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Student is not a volunteer"));
        }
        if (Boolean.TRUE.equals(student.getIsPromoted())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Volunteer is already promoted"));
        }

        // Must have at least one check-in
        boolean hasCheckIn = checkInRepository.existsByStudentIdAnyDay(id);
        if (!hasCheckIn) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Volunteer has not been scanned yet"));
        }

        // Validate all rooms exist and match the given day
        for (PromoteRequest.Assignment a : request.getAssignments()) {
            Room room = roomRepository.findById(a.getRoomId())
                    .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));
            if (!room.getDay().equals(a.getDay())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Room " + a.getRoomId()
                                + " is not assigned to " + a.getDay()));
            }
        }

        // Create Verifier record — username = regNo, copy passwordHash
        Verifier verifier = Verifier.builder()
                .name(student.getName())
                .username(student.getRegNo())
                .passwordHash(student.getPasswordHash())
                .build();
        Verifier savedVerifier = verifierRepository.save(verifier);

        // Create VerifierAssignment rows
        for (PromoteRequest.Assignment a : request.getAssignments()) {
            Room room = roomRepository.findById(a.getRoomId()).orElseThrow();
            VerifierAssignment va = VerifierAssignment.builder()
                    .verifier(savedVerifier)
                    .room(room)
                    .day(a.getDay())
                    .build();
            verifierAssignmentRepository.save(va);
        }

        // Mark student as promoted — blocks their student login
        student.setIsPromoted(true);
        studentRepository.save(student);

        return ResponseEntity.ok(Map.of(
                "verifierId", savedVerifier.getId(),
                "username", savedVerifier.getUsername(),
                "message", "Volunteer promoted to verifier successfully"
        ));
    }
}
