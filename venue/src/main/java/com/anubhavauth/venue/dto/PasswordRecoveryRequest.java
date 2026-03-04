package com.anubhavauth.venue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordRecoveryRequest {

    @NotBlank(message = "Registration number is required")
    private String regNo;
}
