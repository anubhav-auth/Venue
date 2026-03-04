package com.anubhavauth.venue.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInEvent {
    private Long studentId;
    private String studentName;
    private String regNo;
    private Long roomId;
    private String roomName;
    private String seatNumber;
    private String day;
    private String verifierUsername;
    private LocalDateTime checkInTime;
    private long roomCheckedInCount;   // live running total for this room
}
