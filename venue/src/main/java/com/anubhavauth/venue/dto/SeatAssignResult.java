package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SeatAssignResult {
    private boolean alreadyAssigned;
    private Long seatAssignmentId;
    private Long studentId;
    private Long roomId;
    private String seatNumber;  // may be null temporarily
    private String day;
    private LocalDateTime checkInTime;

    public static SeatAssignResult alreadyAssigned(
            com.anubhavauth.venue.entity.SeatAssignment sa) {
        return SeatAssignResult.builder()
                .alreadyAssigned(true)
                .seatAssignmentId(sa.getId())
                .studentId(sa.getStudent().getId())
                .roomId(sa.getRoom().getId())
                .seatNumber(sa.getSeatNumber())
                .day(sa.getDay())
                .checkInTime(sa.getCreatedAt())
                .build();
    }

    public static SeatAssignResult newAssignment(
            com.anubhavauth.venue.entity.SeatAssignment sa, String seatNumber) {
        return SeatAssignResult.builder()
                .alreadyAssigned(false)
                .seatAssignmentId(sa.getId())
                .studentId(sa.getStudent().getId())
                .roomId(sa.getRoom().getId())
                .seatNumber(seatNumber)
                .day(sa.getDay())
                .checkInTime(sa.getCreatedAt())
                .build();
    }
}
