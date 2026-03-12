package com.anubhavauth.venue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String role;
    private String name;

    // Additional fields for verifiers
    private boolean isTeamLead;
    private Long assignedRoomId;

    // Only populated for VERIFIER role
    private List<VerifierAssignmentDto> assignments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifierAssignmentDto {
        private String day;
        private Long roomId;
        private String roomName;
    }
}
