package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.RoomRequest;
import com.anubhavauth.venue.dto.RoomResponse;
import com.anubhavauth.venue.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.createRoom(request));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms(
            @RequestParam(required = false) String day) {
        return ResponseEntity.ok(roomService.getAllRooms(day));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable Long id,
            @Valid @RequestBody RoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.updateRoom(id, request));
        } catch (RuntimeException e) {
            if ("ROOM_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        try {
            roomService.deleteRoom(id);
            return ResponseEntity.ok(Map.of("message", "Room deleted successfully"));
        } catch (RuntimeException e) {
            return switch (e.getMessage()) {
                case "ROOM_NOT_FOUND" -> ResponseEntity.notFound().build();
                case "ROOM_HAS_ASSIGNMENTS" -> ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot delete room with existing seat assignments"));
                default -> throw e;
            };
        }
    }
}
