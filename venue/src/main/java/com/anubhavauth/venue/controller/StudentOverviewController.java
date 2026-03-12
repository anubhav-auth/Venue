package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.StudentOverviewDto;
import com.anubhavauth.venue.entity.RoomRoster;
import com.anubhavauth.venue.entity.SeatAssignment;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.RoomRosterRepository;
import com.anubhavauth.venue.repository.SeatAssignmentRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class StudentOverviewController {

    private final StudentRepository studentRepository;
    private final RoomRosterRepository rosterRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<StudentOverviewDto>> getOverview(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<Student> students = studentRepository.searchStudents(
                search != null && search.isBlank() ? null : search,
                PageRequest.of(page, Math.min(size, 200))
        );

        List<Long> ids = students.map(Student::getId).toList();

        // Room comes from RoomRoster (uploaded via roster CSV)
        Map<Long, List<RoomRoster>> rosterByStudent =
                rosterRepository.findByStudentIdIn(ids)
                        .stream()
                        .collect(Collectors.groupingBy(rr -> rr.getStudent().getId()));

        // Seat number comes from SeatAssignment (only after allocation runs)
        Map<String, SeatAssignment> seatByStudentDay =
                seatAssignmentRepository.findByStudentIdIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                sa -> sa.getStudent().getId() + "_" + sa.getDay(),
                                sa -> sa,
                                (a, b) -> a
                        ));

        Page<StudentOverviewDto> result = students.map(s ->
                StudentOverviewDto.builder()
                        .studentId(s.getId())
                        .regNo(s.getRegNo())
                        .name(s.getName())
                        .degree(s.getDegree())
                        .role(s.getRole())
                        .assignments(
                                rosterByStudent.getOrDefault(s.getId(), List.of())
                                        .stream()
                                        .map(rr -> {
                                            SeatAssignment sa = seatByStudentDay
                                                    .get(s.getId() + "_" + rr.getDay());
                                            return StudentOverviewDto.AssignmentInfo.builder()
                                                    .day(rr.getDay())
                                                    .roomId(rr.getRoom().getId())
                                                    .roomName(rr.getRoom().getRoomName())
                                                    .building(rr.getRoom().getBuilding())
                                                    .floor(rr.getRoom().getFloor())
                                                    .seatNumber(sa != null ? sa.getSeatNumber() : null)
                                                    .build();
                                        })
                                        .toList()
                        )
                        .build()
        );

        return ResponseEntity.ok(result);
    }

}
