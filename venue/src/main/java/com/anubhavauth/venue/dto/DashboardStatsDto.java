package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DashboardStatsDto {

    private OverallStats overall;
    private List<RoomStats> rooms;
    private LocalDateTime lastUpdated;

    @Data
    @Builder
    public static class OverallStats {
        private long totalStudents;
        private long checkedIn;
        private double percentage;
        private long notCheckedIn;
    }

    @Data
    @Builder
    public static class RoomStats {
        private Long roomId;
        private String roomName;
        private String building;
        private String floor;
        private int capacity;
        private long checkedIn;
        private double percentage;
        private String status;   // "high" ≥80%, "medium" 50–79%, "low" <50%
    }
}
