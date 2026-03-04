package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByDay(String day);

    Optional<Room> findByIdAndDay(Long id, String day);
}
