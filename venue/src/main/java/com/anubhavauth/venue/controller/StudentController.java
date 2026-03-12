package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.StudentAssignmentDto;
import com.anubhavauth.venue.dto.StudentProfileDto;
import com.anubhavauth.venue.entity.CheckIn;
import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.CheckInRepository;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentRepository studentRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckInRepository checkInRepository;

    @GetMapping("/assignment")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAssignment(Authentication auth) {
        String regNo = auth.getName();
        Student student = studentRepository.findByRegNo(regNo)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        // VOLUNTEER — return their QR from students table, no seat
        if ("VOLUNTEER".equals(student.getRole())) {
            return ResponseEntity.ok(StudentAssignmentDto.builder()
                    .studentId(student.getId())
                    .name(student.getName())
                    .regNo(student.getRegNo())
                    .role(student.getRole())
                    .qrCodeData(student.getQrCodeData())
                    .build());
        }

        // AUDIENCE — return seat assignment (may be null if not yet assigned on scan)
        Optional<SeatAssignment> saOpt = seatAssignmentRepository.findByStudentId(student.getId());
        if (saOpt.isEmpty()) {
            return ResponseEntity.ok(StudentAssignmentDto.builder()
                    .studentId(student.getId())
                    .name(student.getName())
                    .regNo(student.getRegNo())
                    .role(student.getRole())
                    .qrCodeData(student.getQrCodeData())
                    .checkedIn(false)
                    .build());
        }

        SeatAssignment sa = saOpt.get();

        // Determine if student has checked in on this day
        Optional<CheckIn> checkInOpt = checkInRepository
                .findAll().stream()
                .filter(c -> c.getStudent().getId().equals(student.getId())
                        && c.getDay().equals(sa.getDay())
                        && c.getDeletedAt() == null)
                .findFirst();

        return ResponseEntity.ok(StudentAssignmentDto.builder()
                .studentId(student.getId())
                .name(student.getName())
                .regNo(student.getRegNo())
                .role(student.getRole())
                .roomName(sa.getRoom().getRoomName())
                .building(sa.getRoom().getBuilding())
                .floor(sa.getRoom().getFloor())
                .seatNumber(sa.getSeatNumber())  // null until checked in
                .day(sa.getDay())
                .qrCodeData(sa.getQrCodeData() != null ? sa.getQrCodeData() : student.getQrCodeData())
                .checkedIn(checkInOpt.isPresent())
                .checkInTime(checkInOpt.map(CheckIn::getCheckInTime).orElse(null))
                .build());
    }

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public ResponseEntity<StudentProfileDto> getProfile(Authentication auth) {
        String regNo = auth.getName();
        Student student = studentRepository.findByRegNo(regNo)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        return ResponseEntity.ok(StudentProfileDto.builder()
                .id(student.getId())
                .regNo(student.getRegNo())
                .name(student.getName())
                .email(student.getEmail())
                .degree(student.getDegree())
                .passoutYear(student.getPassoutYear())
                .contactNo(student.getContactNo())
                .role(student.getRole())
                .isPromoted(student.getIsPromoted())
                .build());
    }
}
