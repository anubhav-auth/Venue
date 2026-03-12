package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomResponse {

    private Long id;
    private String roomName;
    private Integer capacity;
    private Integer seatsPerRow;
    private String building;
    private String floor;
    private String day;
    private int skipRows;
    private LocalDateTime createdAt;
}
