package com.anubhavauth.venue.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportResult {

    private int imported;
    private int skipped;
    private int errors;
    private List<RowError> rowErrors;

    @Data
    @Builder
    public static class RowError {
        private int row;
        private String reason;
    }
}
