package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.entity.*;
import com.anubhavauth.venue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportController {

    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckInRepository checkInRepository;
    private final VerifierRepository verifierRepository;
    private final VerifierAssignmentRepository verifierAssignmentRepository;
    private final RoomRepository roomRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 12.1 — Full attendance: every student, their seat, check-in status
    @GetMapping("/attendance")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> fullAttendance(
            @RequestParam(required = false) String day) {

        List<SeatAssignment> assignments = (day != null && !day.isBlank())
                ? seatAssignmentRepository.findAll().stream()
                .filter(sa -> sa.getDay().equals(day)).toList()
                : seatAssignmentRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("RegNo,Name,LastName,Degree,PassoutYear,Room,Building,Floor,SeatNumber,Day,CheckedIn,CheckInTime,VerifiedBy\n");

        for (SeatAssignment sa : assignments) {
            Student s = sa.getStudent();
            Room r = sa.getRoom();

            // Find check-in for this student+day
            CheckIn checkIn = checkInRepository.findAll().stream()
                    .filter(c -> c.getStudent().getId().equals(s.getId())
                            && c.getDay().equals(sa.getDay())
                            && c.getDeletedAt() == null)
                    .findFirst().orElse(null);

            csv.append(escape(s.getRegNo())).append(",")
                    .append(escape(s.getName())).append(",")
                    .append(escape(s.getLastName())).append(",")
                    .append(escape(s.getDegree())).append(",")
                    .append(s.getPassoutYear()).append(",")
                    .append(escape(r.getRoomName())).append(",")
                    .append(escape(r.getBuilding())).append(",")
                    .append(escape(r.getFloor())).append(",")
                    .append(sa.getSeatNumber() != null ? escape(sa.getSeatNumber()) : "OVERFLOW").append(",")
                    .append(escape(sa.getDay())).append(",")
                    .append(checkIn != null ? "YES" : "NO").append(",")
                    .append(checkIn != null && checkIn.getCheckInTime() != null
                            ? checkIn.getCheckInTime().format(FMT) : "").append(",")
                    .append(checkIn != null && checkIn.getVerifier() != null
                            ? escape(checkIn.getVerifier().getUsername()) : "")
                    .append("\n");
        }

        return csvResponse(csv.toString(), "attendance" + (day != null ? "_" + day : "") + ".csv");
    }

    // 12.2 — Room summary: per-room totals
    @GetMapping("/room-summary")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> roomSummary(
            @RequestParam(required = false) String day) {

        List<Room> rooms = (day != null && !day.isBlank())
                ? roomRepository.findByDay(day)
                : roomRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("RoomId,RoomName,Building,Floor,Day,Capacity,Assigned,CheckedIn,NotCheckedIn,Percentage\n");

        for (Room r : rooms) {
            int assigned = seatAssignmentRepository.findByRoomIdAndDay(r.getId(), r.getDay()).size();
            long checkedIn = checkInRepository.countByRoomIdAndDayAndDeletedAtIsNull(r.getId(), r.getDay());
            long notCheckedIn = assigned - checkedIn;
            double pct = assigned > 0 ? Math.round((checkedIn * 100.0 / assigned) * 10.0) / 10.0 : 0.0;

            csv.append(r.getId()).append(",")
                    .append(escape(r.getRoomName())).append(",")
                    .append(escape(r.getBuilding())).append(",")
                    .append(escape(r.getFloor())).append(",")
                    .append(escape(r.getDay())).append(",")
                    .append(r.getCapacity()).append(",")
                    .append(assigned).append(",")
                    .append(checkedIn).append(",")
                    .append(notCheckedIn).append(",")
                    .append(pct).append("%")
                    .append("\n");
        }

        return csvResponse(csv.toString(), "room_summary" + (day != null ? "_" + day : "") + ".csv");
    }

    // 12.3 — Not checked in: students with seat assignments but no check-in
    @GetMapping("/not-checked-in")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> notCheckedIn(
            @RequestParam(required = false) String day) {

        List<SeatAssignment> assignments = (day != null && !day.isBlank())
                ? seatAssignmentRepository.findAll().stream()
                .filter(sa -> sa.getDay().equals(day)).toList()
                : seatAssignmentRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("RegNo,Name,LastName,Email,ContactNo,Degree,PassoutYear,Room,Building,Floor,SeatNumber,Day\n");

        for (SeatAssignment sa : assignments) {
            Student s = sa.getStudent();
            boolean checkedIn = checkInRepository
                    .existsByStudentIdAndDayAndDeletedAtIsNull(s.getId(), sa.getDay());
            if (checkedIn) continue;

            Room r = sa.getRoom();
            csv.append(escape(s.getRegNo())).append(",")
                    .append(escape(s.getName())).append(",")
                    .append(escape(s.getLastName())).append(",")
                    .append(escape(s.getEmail())).append(",")
                    .append(escape(s.getContactNo())).append(",")
                    .append(escape(s.getDegree())).append(",")
                    .append(s.getPassoutYear()).append(",")
                    .append(escape(r.getRoomName())).append(",")
                    .append(escape(r.getBuilding())).append(",")
                    .append(escape(r.getFloor())).append(",")
                    .append(sa.getSeatNumber() != null ? escape(sa.getSeatNumber()) : "OVERFLOW").append(",")
                    .append(escape(sa.getDay()))
                    .append("\n");
        }

        return csvResponse(csv.toString(), "not_checked_in" + (day != null ? "_" + day : "") + ".csv");
    }

    // 12.4 — Verifier activity: each verifier, their room, scan count
    @GetMapping("/verifier-activity")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> verifierActivity(
            @RequestParam(required = false) String day) {

        List<Verifier> verifiers = verifierRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("VerifierId,Username,Name,Day,RoomId,RoomName,ScansPerformed\n");

        for (Verifier v : verifiers) {
            List<VerifierAssignment> vAssignments = verifierAssignmentRepository.findAll().stream()
                    .filter(va -> va.getVerifier().getId().equals(v.getId())
                            && (day == null || day.isBlank() || va.getDay().equals(day)))
                    .toList();

            if (vAssignments.isEmpty()) {
                // Verifier with no room assignments — still show them
                csv.append(v.getId()).append(",")
                        .append(escape(v.getUsername())).append(",")
                        .append(escape(v.getName())).append(",")
                        .append(",,,0\n");
                continue;
            }

            for (VerifierAssignment va : vAssignments) {
                long scans = checkInRepository.findAll().stream()
                        .filter(c -> c.getVerifier() != null
                                && c.getVerifier().getId().equals(v.getId())
                                && c.getDay().equals(va.getDay())
                                && c.getDeletedAt() == null)
                        .count();

                csv.append(v.getId()).append(",")
                        .append(escape(v.getUsername())).append(",")
                        .append(escape(v.getName())).append(",")
                        .append(escape(va.getDay())).append(",")
                        .append(va.getRoom().getId()).append(",")
                        .append(escape(va.getRoom().getRoomName())).append(",")
                        .append(scans)
                        .append("\n");
            }
        }

        return csvResponse(csv.toString(), "verifier_activity" + (day != null ? "_" + day : "") + ".csv");
    }

    // Wrap commas/newlines in quotes
    private String escape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private ResponseEntity<byte[]> csvResponse(String content, String filename) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(bytes);
    }
}
