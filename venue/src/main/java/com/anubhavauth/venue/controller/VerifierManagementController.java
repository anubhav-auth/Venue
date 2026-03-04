package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.entity.Verifier;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.repository.VerifierAssignmentRepository;
import com.anubhavauth.venue.repository.VerifierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class VerifierManagementController {

    private final VerifierRepository verifierRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final StudentRepository studentRepository;

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
}
