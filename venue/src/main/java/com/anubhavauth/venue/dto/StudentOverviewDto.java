package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StudentOverviewDto {
    private Long studentId;
    private String regNo;
    private String name;
    private String degree;
    private String role;
    private List<AssignmentInfo> assignments;

    @Data
    @Builder
    public static class AssignmentInfo {
        private String day;
        private Long roomId;
        private String roomName;
        private String building;
        private String floor;
        private String seatNumber;
    }
}
