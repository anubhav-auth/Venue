package com.anubhavauth.venue.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PromoteRequest {

    @NotEmpty(message = "At least one assignment is required")
    private List<Assignment> assignments;

    @Data
    public static class Assignment {
        private String day;
        private Long roomId;
    }
}
