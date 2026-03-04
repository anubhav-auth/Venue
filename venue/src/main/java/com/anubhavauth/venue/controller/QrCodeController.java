package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class QrCodeController {

    private final StudentRepository studentRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final QrCodeGenerator qrCodeGenerator;

    // Audience: returns seat assignment QR PNG
    // Volunteer: returns volunteer identity QR PNG
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getQrCode(
            @RequestParam(required = false) String day,
            Authentication auth) {

        Student student = studentRepository.findByRegNo(auth.getName())
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        String qrContent;

        if ("VOLUNTEER".equals(student.getRole())) {
            qrContent = student.getQrCodeData();
            if (qrContent == null) {
                return ResponseEntity.notFound().build();
            }
        } else {
            // AUDIENCE — find their seat assignment
            Optional<SeatAssignment> saOpt = day != null && !day.isBlank()
                    ? seatAssignmentRepository.findAll().stream()
                    .filter(sa -> sa.getStudent().getId().equals(student.getId())
                            && sa.getDay().equals(day))
                    .findFirst()
                    : seatAssignmentRepository.findByStudentId(student.getId());

            if (saOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            qrContent = saOpt.get().getQrCodeData();
            if (qrContent == null) {
                return ResponseEntity.notFound().build();
            }
        }

        byte[] png = qrCodeGenerator.generate(qrContent);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(png.length)
                .body(png);
    }
}
