package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RoomDetailDto {

    private Long roomId;
    private String roomName;
    private String building;
    private String floor;
    private String day;
    private int capacity;
    private int seatsPerRow;
    private int skipRows;
    private List<VerifierInfo> assignedVerifiers;
    private List<SeatInfo> seats;
    private List<SeatInfo> overflow;

    @Data
    @Builder
    public static class SeatInfo {
        private Long studentId;
        private String name;
        private String regNo;
        private String degree;
        private Integer passoutYear;
        private String seatNumber;    // null for overflow
        private boolean checkedIn;
        private LocalDateTime checkInTime;
        private String verifierUsername;
        private Long checkInId;        // null if not yet checked in
    }

    @Data
    @Builder
    public static class VerifierInfo {
        private Long verifierId;
        private String username;
        private String name;
    }
}
