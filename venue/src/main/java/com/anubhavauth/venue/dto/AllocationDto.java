package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AllocationDto {
    private Long assignmentId;
    private Long studentId;
    private String regNo;
    private String name;
    private String degree;
    private Integer passoutYear;
    private String role;
    private Long roomId;
    private String roomName;
    private String building;
    private String floor;
    private String seatNumber;   // null = overflow
    private String day;
    private boolean overflow;
}
