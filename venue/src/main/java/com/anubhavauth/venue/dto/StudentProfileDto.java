package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentProfileDto {
    private Long id;
    private String regNo;
    private String name;
    private String email;
    private String degree;
    private Integer passoutYear;
    private String contactNo;
    private String role;
    private Boolean isPromoted;
}
