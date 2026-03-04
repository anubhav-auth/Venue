package com.anubhavauth.venue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScanRequest {

    @NotBlank(message = "QR data is required")
    private String qrData;

    private String day; // optional for admin scan, required for verifier scan
}
