package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.SeatAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    boolean existsByStudentId(Long studentId);

    Optional<SeatAssignment> findByStudentId(Long studentId);

    List<SeatAssignment> findByRoomIdAndDay(Long roomId, String day);

    Optional<SeatAssignment> findByStudentIdAndDay(Long studentId, String day);

    long countByRoomIdAndDayAndSeatNumberIsNotNull(Long roomId, String day);

    // Fix 5 — targeted query for report methods (no full table scan)
    List<SeatAssignment> findByDay(String day);

    // Fix 6 — DB-level filter + pagination for the allocations endpoint
    @Query("""
            SELECT sa FROM SeatAssignment sa
            JOIN FETCH sa.student
            JOIN FETCH sa.room
            WHERE (:day IS NULL OR sa.day = :day)
              AND (:roomId IS NULL OR sa.room.id = :roomId)
            """)
    Page<SeatAssignment> findWithFilters(
            @Param("day") String day,
            @Param("roomId") Long roomId,
            Pageable pageable
    );
}

