package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.RoomRoster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomRosterRepository extends JpaRepository<RoomRoster, Long> {

    Page<RoomRoster> findByRoomIdAndDay(Long roomId, String day, Pageable pageable);

    java.util.List<RoomRoster> findByRoomIdAndDay(Long roomId, String day);

    boolean existsByRoomIdAndStudentIdAndDay(Long roomId, Long studentId, String day);

    long countByRoomIdAndDay(Long roomId, String day);

    @Modifying
    @Query("DELETE FROM RoomRoster r WHERE r.room.id = :roomId AND r.day = :day")
    void deleteByRoomIdAndDay(@Param("roomId") Long roomId, @Param("day") String day);

    List<RoomRoster> findByStudentIdIn(List<Long> studentIds);
    boolean existsByStudentIdAndDay(Long studentId, String day);
}
