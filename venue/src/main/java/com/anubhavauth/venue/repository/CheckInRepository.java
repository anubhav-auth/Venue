package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByStudentIdAndDayAndDeletedAtIsNull(Long studentId, String day);

    @Query("SELECT COUNT(c) > 0 FROM CheckIn c WHERE c.student.id = :studentId AND c.deletedAt IS NULL")
    boolean existsByStudentIdAnyDay(@Param("studentId") Long studentId);

    long countByRoomIdAndDayAndDeletedAtIsNull(Long roomId, String day);

    Optional<CheckIn> findByStudentIdAndDayAndDeletedAtIsNull(Long studentId, String day);

    List<CheckIn> findByRoomIdAndDayAndDeletedAtIsNull(Long roomId, String day);

    // Fix 5 — batch-load all check-ins for a given day (no per-row calls)
    List<CheckIn> findByDayAndDeletedAtIsNull(String day);
}
