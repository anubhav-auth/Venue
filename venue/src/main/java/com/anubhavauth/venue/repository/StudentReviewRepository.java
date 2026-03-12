package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.StudentReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentReviewRepository extends JpaRepository<StudentReview, Long> {

    List<StudentReview> findByStudentIdAndDay(Long studentId, String day);
}
