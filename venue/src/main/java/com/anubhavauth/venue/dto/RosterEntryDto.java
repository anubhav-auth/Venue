package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RosterEntryDto {
    private Long id;
    private Long studentId;
    private String regNo;
    private String name;
    private String degree;
    private String day;
    private LocalDateTime uploadedAt;
}
