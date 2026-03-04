package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.LoginResponse;
import com.anubhavauth.venue.entity.AdminUser;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.entity.Verifier;
import com.anubhavauth.venue.repository.AdminUserRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.repository.VerifierRepository;
import com.anubhavauth.venue.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final VerifierRepository verifierRepository;
    private final StudentRepository studentRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(String username, String password) {

        // 1. Try admin_users table
        Optional<AdminUser> adminOpt = adminUserRepository.findByUsername(username);
        if (adminOpt.isPresent()) {
            AdminUser admin = adminOpt.get();
            if (passwordEncoder.matches(password, admin.getPasswordHash())) {
                String token = jwtUtil.generateToken(
                        admin.getId(), admin.getUsername(), "ADMIN", admin.getFullName()
                );
                return LoginResponse.builder()
                        .token(token)
                        .userId(admin.getId())
                        .username(admin.getUsername())
                        .role("ADMIN")
                        .name(admin.getFullName())
                        .build();
            }
        }

        // 2. Try verifiers table
        Optional<Verifier> verifierOpt = verifierRepository.findByUsername(username);
        if (verifierOpt.isPresent()) {
            Verifier verifier = verifierOpt.get();
            if (passwordEncoder.matches(password, verifier.getPasswordHash())) {
                String token = jwtUtil.generateToken(
                        verifier.getId(), verifier.getUsername(), "VERIFIER", verifier.getName()
                );
                List<LoginResponse.VerifierAssignmentDto> assignments = verifier.getAssignments()
                        .stream()
                        .map(a -> LoginResponse.VerifierAssignmentDto.builder()
                                .day(a.getDay())
                                .roomId(a.getRoom().getId())
                                .roomName(a.getRoom().getRoomName())
                                .build())
                        .toList();
                return LoginResponse.builder()
                        .token(token)
                        .userId(verifier.getId())
                        .username(verifier.getUsername())
                        .role("VERIFIER")
                        .name(verifier.getName())
                        .assignments(assignments)
                        .build();
            }
        }

        // 3. Try students table — skip promoted volunteers
        Optional<Student> studentOpt = studentRepository.findByRegNo(username);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            if (Boolean.TRUE.equals(student.getIsPromoted())) {
                throw new RuntimeException("UNAUTHORIZED");
            }
            if (passwordEncoder.matches(password, student.getPasswordHash())) {
                String token = jwtUtil.generateToken(
                        student.getId(), student.getRegNo(), student.getRole(), student.getName()
                );
                return LoginResponse.builder()
                        .token(token)
                        .userId(student.getId())
                        .username(student.getRegNo())
                        .role(student.getRole())
                        .name(student.getName())
                        .build();
            }
        }

        throw new RuntimeException("UNAUTHORIZED");
    }
}
