package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AllocationResultDto {
    private int day1Count;
    private int day2Count;
    private int overflowCount;
    private int totalAllocated;
    private List<RoomBreakdown> roomBreakdown;

    @Data
    @Builder
    public static class RoomBreakdown {
        private Long roomId;
        private String roomName;
        private String day;
        private int capacity;
        private int assigned;
        private int overflow;
    }
}
