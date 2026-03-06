package com.anubhavauth.venue.service;

import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.entity.Student;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.repository.StudentRepository;
import com.anubhavauth.venue.util.HashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveStudents(List<Student> students) {
        studentRepository.saveAll(students);
    }

    @Transactional
    public void saveVolunteers(List<Student> students) throws Exception {
        // First save — get generated IDs
        List<Student> saved = studentRepository.saveAll(students);
        // Second pass — set QR using real ID
        for (Student vol : saved) {
            String hash = hashService.generateHash(vol.getId() + "VOLUNTEER");
            vol.setQrCodeData(objectMapper.writeValueAsString(Map.of(
                    "studentId", vol.getId(),
                    "role", "VOLUNTEER",
                    "hash", hash
            )));
        }
        studentRepository.saveAll(saved);
    }

    @Transactional
    public void saveRooms(List<Room> rooms) {
        roomRepository.saveAll(rooms);
    }
}
