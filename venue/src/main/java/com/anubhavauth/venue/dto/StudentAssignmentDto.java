package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StudentAssignmentDto {
    private Long studentId;
    private String name;
    private String regNo;
    private String role;

    // Seat details (null for volunteers / unallocated)
    private String roomName;
    private String building;
    private String floor;
    private String seatNumber;   // null until checked in
    private String day;
    private String qrCodeData;

    // Check-in status — populated after verifier scans student QR
    private boolean checkedIn;
    private LocalDateTime checkInTime;
}
