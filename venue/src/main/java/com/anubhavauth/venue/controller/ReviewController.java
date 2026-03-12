package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ReviewDto;
import com.anubhavauth.venue.service.ReviewService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** POST /api/checkin/{checkInId}/review — VERIFIER | TEAM_LEAD | ADMIN */
    @PostMapping("/api/checkin/{checkInId}/review")
    public ResponseEntity<ReviewDto> addReview(
            @PathVariable Long checkInId,
            @RequestBody AddReviewRequest body,
            Authentication auth) {

        // studentId and day are passed in the request body alongside reviewText
        ReviewDto saved = reviewService.addReview(
                body.getStudentId(),
                checkInId,
                body.getReviewText(),
                auth.getName(),
                body.getDay()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** GET /api/admin/students/{studentId}/reviews?day=  — ADMIN only */
    @GetMapping("/api/admin/students/{studentId}/reviews")
    public ResponseEntity<List<ReviewDto>> getReviews(
            @PathVariable Long studentId,
            @RequestParam String day) {

        return ResponseEntity.ok(reviewService.getReviews(studentId, day));
    }

    @Data
    public static class AddReviewRequest {
        private Long studentId;
        private String day;
        private String reviewText;
    }
}
