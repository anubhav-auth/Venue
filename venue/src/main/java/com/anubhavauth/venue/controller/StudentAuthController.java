package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.PasswordRecoveryRequest;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.StudentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/student/auth")
@RequiredArgsConstructor
public class StudentAuthController {

    private final StudentRepository studentRepository;

    @PostMapping("/recover-password")
    public ResponseEntity<?> recoverPassword(@Valid @RequestBody PasswordRecoveryRequest request) {
        Optional<Student> studentOpt = studentRepository.findByRegNo(request.getRegNo());

        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();

        String password = student.getRegNo().toLowerCase();
        return ResponseEntity.ok(Map.of("password", password));
    }
}
