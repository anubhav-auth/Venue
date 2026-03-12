package com.anubhavauth.venue.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HashService {

    @Value("${app.qr-secret}")
    private String secret;

    public String generateHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest((data + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // Used by: verifyHash(studentId + "VOLUNTEER", hash) in VerifierController
    public boolean verifyHash(String data, String hash) {
        return generateHash(data).equals(hash);
    }

    // New-style audience QR (no roomId in payload) — matches CsvImportService: generateHash(id + "AUDIENCE")
    public boolean verifyHash(Long studentId, String role, String hash) {
        return generateHash(studentId + role).equals(hash);  // e.g. "10096AUDIENCE"
    }

    // Legacy allocation QR (roomId + day baked in) — matches AllocationService: generateHash(studentId + roomId)
    public boolean verifyHash(Long studentId, Long roomId, String hash) {
        return generateHash(studentId + "" + roomId).equals(hash); // e.g. "100961"
    }
}
