package com.anubhavauth.venue.service;

import com.anubhavauth.venue.dto.AllocationResultDto;
import com.anubhavauth.venue.dto.AllocationSummaryDto;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.HashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AllocationService {

    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AllocationResultDto allocateAll() {
        // Guard: already allocated
        long existingCount = seatAssignmentRepository.count();
        if (existingCount > 0) {
            throw new RuntimeException("ALREADY_ALLOCATED");
        }

        // Fetch unallocated AUDIENCE students
        List<Student> audience = studentRepository.findByRole("AUDIENCE").stream()
                .filter(s -> !seatAssignmentRepository.existsByStudentId(s.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Shuffle randomly
        Collections.shuffle(audience);

        List<Room> day1Rooms = roomRepository.findByDay("day1");
        List<Room> day2Rooms = roomRepository.findByDay("day2");

        int day1Capacity = day1Rooms.stream().mapToInt(Room::getCapacity).sum();
        int day2Capacity = day2Rooms.stream().mapToInt(Room::getCapacity).sum();

        // Slice the shuffled list
        List<Student> day1Students  = audience.subList(0, Math.min(day1Capacity, audience.size()));
        int day2End = Math.min(day1Capacity + day2Capacity, audience.size());
        List<Student> day2Students  = audience.subList(day1Students.size(), day2End);
        List<Student> overflow      = audience.subList(day2End, audience.size());

        List<SeatAssignment> assignments = new ArrayList<>();
        Map<Long, AllocationResultDto.RoomBreakdown> breakdownMap = new LinkedHashMap<>();

        // Allocate day1
        allocateToRooms(day1Students, day1Rooms, "day1", assignments, breakdownMap);
        // Allocate day2
        allocateToRooms(day2Students, day2Rooms, "day2", assignments, breakdownMap);
        // Overflow → largest day2 room, seatNumber = NULL
        if (!overflow.isEmpty() && !day2Rooms.isEmpty()) {
            Room overflowRoom = day2Rooms.stream()
                    .max(Comparator.comparingInt(Room::getCapacity))
                    .orElseThrow();
            for (Student s : overflow) {
                SeatAssignment sa = SeatAssignment.builder()
                        .student(s)
                        .room(overflowRoom)
                        .seatNumber(null)
                        .day("day2")
                        .build();
                assignments.add(sa);
                breakdownMap.computeIfAbsent(overflowRoom.getId(), id ->
                        AllocationResultDto.RoomBreakdown.builder()
                                .roomId(overflowRoom.getId())
                                .roomName(overflowRoom.getRoomName())
                                .day("day2")
                                .capacity(overflowRoom.getCapacity())
                                .assigned(0).overflow(0).build()
                ).setOverflow(breakdownMap.get(overflowRoom.getId()).getOverflow() + 1);
            }
        }

        // Batch save in chunks of 500
        List<SeatAssignment> saved = new ArrayList<>();
        for (int i = 0; i < assignments.size(); i += 500) {
            List<SeatAssignment> chunk = assignments.subList(i, Math.min(i + 500, assignments.size()));
            saved.addAll(seatAssignmentRepository.saveAll(chunk));
        }

        // Generate QR codes for all saved assignments
        for (SeatAssignment sa : saved) {
            if (sa.getSeatNumber() != null) { // skip overflow for QR? No — overflow gets QR too
                try {
                    String hash = hashService.generateHash(sa.getStudent().getId() + "" + sa.getRoom().getId());
                    Map<String, Object> qrPayload = new LinkedHashMap<>();
                    qrPayload.put("studentId", sa.getStudent().getId());
                    qrPayload.put("regNo", sa.getStudent().getRegNo());
                    qrPayload.put("roomId", sa.getRoom().getId());
                    qrPayload.put("seatNumber", sa.getSeatNumber()); // null for overflow
                    qrPayload.put("day", sa.getDay());
                    qrPayload.put("hash", hash);
                    sa.setQrCodeData(objectMapper.writeValueAsString(qrPayload));
                } catch (Exception e) {
                    throw new RuntimeException("QR generation failed", e);
                }
            }
        }
        seatAssignmentRepository.saveAll(saved);

        return AllocationResultDto.builder()
                .day1Count(day1Students.size())
                .day2Count(day2Students.size())
                .overflowCount(overflow.size())
                .totalAllocated(audience.size())
                .roomBreakdown(new ArrayList<>(breakdownMap.values()))
                .build();
    }

    private void allocateToRooms(List<Student> students, List<Room> rooms, String day,
                                 List<SeatAssignment> assignments,
                                 Map<Long, AllocationResultDto.RoomBreakdown> breakdownMap) {
        int studentIndex = 0;
        for (Room room : rooms) {
            int seatsInRoom = 0;
            for (int pos = 0; pos < room.getCapacity() && studentIndex < students.size(); pos++) {
                Student student = students.get(studentIndex++);
                String seatNumber = generateSeatNumber(pos, room.getSeatsPerRow());
                assignments.add(SeatAssignment.builder()
                        .student(student)
                        .room(room)
                        .seatNumber(seatNumber)
                        .day(day)
                        .build());
                seatsInRoom++;
            }
            breakdownMap.put(room.getId(), AllocationResultDto.RoomBreakdown.builder()
                    .roomId(room.getId())
                    .roomName(room.getRoomName())
                    .day(day)
                    .capacity(room.getCapacity())
                    .assigned(seatsInRoom)
                    .overflow(0)
                    .build());
            if (studentIndex >= students.size()) break;
        }
    }

    // Position 0 → A-01, Position 14 (seatsPerRow=15) → A-15, Position 15 → B-01
    // Position 390 (seatsPerRow=15, after Z=25 rows) → AA-01
    public String generateSeatNumber(int position, int seatsPerRow) {
        int row = position / seatsPerRow;
        int col = (position % seatsPerRow) + 1;
        return rowLabel(row) + "-" + String.format("%02d", col);
    }

    private String rowLabel(int row) {
        StringBuilder label = new StringBuilder();
        row++; // 1-indexed
        while (row > 0) {
            row--;
            label.insert(0, (char) ('A' + row % 26));
            row /= 26;
        }
        return label.toString();
    }

    @Transactional(readOnly = true)
    public List<AllocationSummaryDto> getSummary() {
        return roomRepository.findAll().stream().map(room -> {
            int assigned = seatAssignmentRepository.findByRoomIdAndDay(room.getId(), room.getDay()).size();
            return AllocationSummaryDto.builder()
                    .roomId(room.getId())
                    .roomName(room.getRoomName())
                    .day(room.getDay())
                    .capacity(room.getCapacity())
                    .assigned(assigned)
                    .availableSeats(Math.max(0, room.getCapacity() - assigned))
                    .build();
        }).toList();
    }

    @Transactional
    public void clearAllocations() {
        seatAssignmentRepository.deleteAll();
    }
}
