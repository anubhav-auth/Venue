package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class AdminQrController {

    private final StudentRepository studentRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final QrCodeGenerator qrCodeGenerator;

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getStudentQr(
            @PathVariable Long id,
            @RequestParam(required = false) String day) {

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        String qrContent;

        if ("VOLUNTEER".equals(student.getRole())) {
            qrContent = student.getQrCodeData();
        } else {
            Optional<SeatAssignment> saOpt = day != null && !day.isBlank()
                    ? seatAssignmentRepository.findAll().stream()
                    .filter(sa -> sa.getStudent().getId().equals(id)
                            && sa.getDay().equals(day))
                    .findFirst()
                    : seatAssignmentRepository.findByStudentId(id);

            if (saOpt.isEmpty() || saOpt.get().getQrCodeData() == null) {
                return ResponseEntity.notFound().build();
            }
            qrContent = saOpt.get().getQrCodeData();
        }

        if (qrContent == null) return ResponseEntity.notFound().build();

        byte[] png = qrCodeGenerator.generate(qrContent);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(png.length)
                .body(png);
    }
}
