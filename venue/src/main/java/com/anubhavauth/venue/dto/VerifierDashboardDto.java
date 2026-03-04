package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerifierDashboardDto {
    private String verifierName;
    private Long roomId;
    private String roomName;
    private String building;
    private String floor;
    private String day;
    private int capacity;
    private long checkedInCount;
    private long remaining;
    private double percentage;
    private String status; // "high" ≥80%, "medium" 50–79%, "low" <50%
}
