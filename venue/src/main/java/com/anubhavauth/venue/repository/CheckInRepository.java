package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    // Active check-in = deleted_at IS NULL
    boolean existsByStudentIdAndDayAndDeletedAtIsNull(Long studentId, String day);

    long countByRoomIdAndDayAndDeletedAtIsNull(Long roomId, String day);

    // Check if student has any active check-in across any day
    @Query("""
        SELECT COUNT(c) > 0 FROM CheckIn c
        WHERE c.student.id = :studentId
        AND c.deletedAt IS NULL
    """)
    boolean existsByStudentIdAnyDay(@Param("studentId") Long studentId);
}
