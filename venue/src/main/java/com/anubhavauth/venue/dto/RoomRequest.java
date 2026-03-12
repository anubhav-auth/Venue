package com.anubhavauth.venue.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RoomRequest {

    @NotBlank(message = "Room name is required")
    private String roomName;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be greater than 0")
    private Integer capacity;

    @Min(value = 1, message = "Seats per row must be greater than 0")
    private Integer seatsPerRow = 10;

    @NotBlank(message = "Building is required")
    private String building;

    @NotBlank(message = "Floor is required")
    private String floor;

    @NotBlank(message = "Day is required")
    @Pattern(regexp = "day1|day2", message = "Day must be 'day1' or 'day2'")
    private String day;

    @Min(value = 0, message = "Skip rows must be 0 or greater")
    private int skipRows = 0;
}
