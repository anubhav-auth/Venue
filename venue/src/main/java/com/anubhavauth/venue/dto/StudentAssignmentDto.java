package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

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
    private String seatNumber;
    private String day;
    private String qrCodeData;
}
