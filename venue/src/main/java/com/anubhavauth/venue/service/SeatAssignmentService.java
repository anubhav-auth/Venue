package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.SeatAssignResult;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.RoomRosterRepository;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles on-scan seat assignment (dynamic assignment when verifier scans QR).
 * Uses SELECT FOR UPDATE (pessimistic locking) on the Room row to prevent
 * concurrent seat conflicts.
 */
@Service
@RequiredArgsConstructor
public class SeatAssignmentService {

    private final RoomRepository roomRepository;
    private final RoomRosterRepository rosterRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public SeatAssignResult assignSeatOnScan(Long roomId, Long studentId, String day) {
        // 1. Pessimistic lock the Room row to prevent concurrent seat number conflicts
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

        // 2. Verify student is on the roster for this room+day
        if (!rosterRepository.existsByRoomIdAndStudentIdAndDay(roomId, studentId, day)) {
            throw new RuntimeException("STUDENT_NOT_ON_ROSTER");
        }

        // 3. Check if seat already assigned for this student+day
        Optional<SeatAssignment> existing =
                seatAssignmentRepository.findByStudentIdAndDay(studentId, day);
        if (existing.isPresent() && existing.get().getSeatNumber() != null) {
            return SeatAssignResult.alreadyAssigned(existing.get());
        }

        // 4. Calculate next available seat
        long assigned = seatAssignmentRepository
                .countByRoomIdAndDayAndSeatNumberIsNotNull(roomId, day);

        int seatsPerRow = room.getSeatsPerRow() != null ? room.getSeatsPerRow() : 10;
        int row = (int) (assigned / seatsPerRow);
        int col = (int) (assigned % seatsPerRow);
        String seatNumber = rowLabel(row) + "-" + String.format("%02d", col + 1);

        // 5. Save or update the SeatAssignment
        com.anubhavauth.venue.entity.Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("STUDENT_NOT_FOUND"));

        SeatAssignment sa;
        if (existing.isPresent()) {
            sa = existing.get();
            sa.setSeatNumber(seatNumber);
        } else {
            sa = SeatAssignment.builder()
                    .student(student)
                    .room(room)
                    .day(day)
                    .seatNumber(seatNumber)
                    .build();
        }
        seatAssignmentRepository.save(sa);

        return SeatAssignResult.newAssignment(sa, seatNumber);
    }

    /**
     * Convert 0-based row index to Excel-style column label:
     * 0→A, 1→B, ..., 25→Z, 26→AA, 27→AB, ...
     * Mirrors the existing AllocationService.rowLabel() logic.
     */
    String rowLabel(int row) {
        StringBuilder label = new StringBuilder();
        row++; // 1-indexed
        while (row > 0) {
            row--;
            label.insert(0, (char) ('A' + row % 26));
            row /= 26;
        }
        return label.toString();
    }
}
