package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.RoomRequest;
import com.anubhavauth.venue.dto.RoomResponse;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;

    public RoomResponse createRoom(RoomRequest request) {
        Room room = Room.builder()
                .roomName(request.getRoomName())
                .capacity(request.getCapacity())
                .seatsPerRow(request.getSeatsPerRow())
                .building(request.getBuilding())
                .floor(request.getFloor())
                .day(request.getDay())
                .build();
        return toResponse(roomRepository.save(room));
    }

    public List<RoomResponse> getAllRooms(String day) {
        List<Room> rooms = (day != null && !day.isBlank())
                ? roomRepository.findByDay(day)
                : roomRepository.findAll();
        return rooms.stream().map(this::toResponse).toList();
    }

    public RoomResponse updateRoom(Long id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

        room.setRoomName(request.getRoomName());
        room.setCapacity(request.getCapacity());
        room.setSeatsPerRow(request.getSeatsPerRow());
        room.setBuilding(request.getBuilding());
        room.setFloor(request.getFloor());
        room.setDay(request.getDay());

        return toResponse(roomRepository.save(room));
    }

    public void deleteRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

        boolean hasAssignments = !seatAssignmentRepository.findByRoomIdAndDay(id, room.getDay()).isEmpty();
        if (hasAssignments) {
            throw new RuntimeException("ROOM_HAS_ASSIGNMENTS");
        }

        roomRepository.delete(room);
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomName(room.getRoomName())
                .capacity(room.getCapacity())
                .seatsPerRow(room.getSeatsPerRow())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .day(room.getDay())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
