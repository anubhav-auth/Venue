package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AllocationSummaryDto {
    private Long roomId;
    private String roomName;
    private String day;
    private int capacity;
    private int assigned;
    private int availableSeats;
}
