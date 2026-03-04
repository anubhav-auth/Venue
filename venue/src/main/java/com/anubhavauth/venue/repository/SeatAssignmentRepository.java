package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.SeatAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    List<SeatAssignment> findByRoomIdAndDay(Long roomId, String day);

    Optional<SeatAssignment> findByStudentId(Long studentId);

    boolean existsByStudentId(Long studentId);

    Optional<SeatAssignment> findByRoomIdAndSeatNumberAndDay(Long roomId, String seatNumber, String day);
}
