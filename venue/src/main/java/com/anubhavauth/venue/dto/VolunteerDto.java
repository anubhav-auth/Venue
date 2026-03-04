package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VolunteerDto {
    private Long studentId;
    private String regNo;
    private String name;
    private String email;
    private Boolean day1Attended;
    private Boolean day2Attended;
    private Boolean isPromoted;
    private Boolean canPromote;
}
