package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.SeatAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    boolean existsByStudentId(Long studentId);

    Optional<SeatAssignment> findByStudentId(Long studentId);

    List<SeatAssignment> findByRoomIdAndDay(Long roomId, String day);
}
