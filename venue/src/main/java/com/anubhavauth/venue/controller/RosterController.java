package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.RosterEntryDto;
import com.anubhavauth.venue.service.RosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/rooms/{roomId}/roster")
@RequiredArgsConstructor
public class RosterController {

    private final RosterService rosterService;

    /** POST — upload CSV, returns { jobId } immediately */
    @PostMapping
    public ResponseEntity<?> uploadRoster(
            @PathVariable Long roomId,
            @RequestParam String day,
            @RequestParam("file") MultipartFile file) throws Exception {

        String jobId = rosterService.startRosterUpload(roomId, day, file);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    /** GET /status/{jobId} — poll upload status */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getUploadStatus(
            @PathVariable Long roomId,
            @PathVariable String jobId) {

        Map<String, Object> status = rosterService.getUploadStatus(jobId);
        if ("NOT_FOUND".equals(status.get("status"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /** GET — paginated roster list */
    @GetMapping
    public ResponseEntity<Page<RosterEntryDto>> getRoster(
            @PathVariable Long roomId,
            @RequestParam String day,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<RosterEntryDto> result = rosterService.getRoster(
                roomId, day, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    /** DELETE — clear roster for a room+day */
    @DeleteMapping
    public ResponseEntity<?> clearRoster(
            @PathVariable Long roomId,
            @RequestParam String day) {

        rosterService.clearRoster(roomId, day);
        return ResponseEntity.ok(Map.of("message", "Roster cleared"));
    }
}
