package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.ReviewDto;
import com.anubhavauth.venue.entity.StudentReview;
import com.anubhavauth.venue.repository.StudentReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final StudentReviewRepository reviewRepository;

    @Transactional
    public ReviewDto addReview(Long studentId, Long checkInId, String reviewText,
                               String addedByUsername, String day) {
        StudentReview review = StudentReview.builder()
                .studentId(studentId)
                .checkInId(checkInId)
                .day(day)
                .reviewText(reviewText)
                .addedByUsername(addedByUsername)
                .build();
        return toDto(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getReviews(Long studentId, String day) {
        return reviewRepository.findByStudentIdAndDay(studentId, day)
                .stream().map(this::toDto).toList();
    }

    private ReviewDto toDto(StudentReview r) {
        return ReviewDto.builder()
                .id(r.getId())
                .studentId(r.getStudentId())
                .checkInId(r.getCheckInId())
                .day(r.getDay())
                .reviewText(r.getReviewText())
                .addedByUsername(r.getAddedByUsername())
                .addedAt(r.getAddedAt())
                .build();
    }
}
