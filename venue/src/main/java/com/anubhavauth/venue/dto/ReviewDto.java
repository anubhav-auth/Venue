package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewDto {
    private Long id;
    private Long studentId;
    private Long checkInId;
    private String day;
    private String reviewText;
    private String addedByUsername;
    private LocalDateTime addedAt;
}
