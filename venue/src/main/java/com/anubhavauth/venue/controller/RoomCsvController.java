package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.util.CsvColumnResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class RoomCsvController {

    private final RoomRepository roomRepository;
    private final CsvColumnResolver csvColumnResolver;

    @PostMapping("/upload")
    public ResponseEntity<ImportResult> uploadRooms(@RequestParam("file") MultipartFile file) {
        List<ImportResult.RowError> rowErrors = new ArrayList<>();
        List<Room> toSave = new ArrayList<>();
        int imported = 0, skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return ResponseEntity.badRequest().body(
                        ImportResult.builder().errors(1)
                                .rowErrors(List.of(ImportResult.RowError.builder()
                                        .row(0).reason("Empty file").build()))
                                .build());
            }

            Map<String, String> columnDefs = csvColumnResolver.getRoom().get("columns");
            Map<String, Integer> indexMap = csvColumnResolver.resolveHeaders(headerLine, columnDefs);

            // Validate required columns present
            for (String required : List.of("roomName", "capacity", "building", "floor", "day")) {
                if (!indexMap.containsKey(required)) {
                    return ResponseEntity.badRequest().body(
                            ImportResult.builder().errors(1)
                                    .rowErrors(List.of(ImportResult.RowError.builder()
                                            .row(0).reason("Missing required column: " + required).build()))
                                    .build());
                }
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);

                String roomName = csvColumnResolver.getValue(cols, indexMap, "roomName");
                String capacityStr = csvColumnResolver.getValue(cols, indexMap, "capacity");
                String building = csvColumnResolver.getValue(cols, indexMap, "building");
                String floor = csvColumnResolver.getValue(cols, indexMap, "floor");
                String day = csvColumnResolver.getValue(cols, indexMap, "day");
                String seatsPerRowStr = csvColumnResolver.getValue(cols, indexMap, "seatsPerRow");

                // Validate required fields
                if (roomName == null || capacityStr == null || building == null
                        || floor == null || day == null) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Missing required field(s)").build());
                    skipped++;
                    continue;
                }

                // Validate day
                if (!day.equals("day1") && !day.equals("day2")) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Invalid day value: " + day).build());
                    skipped++;
                    continue;
                }

                // Parse capacity
                int capacity;
                try {
                    capacity = Integer.parseInt(capacityStr.trim());
                    if (capacity <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    rowErrors.add(ImportResult.RowError.builder()
                            .row(rowNum).reason("Invalid capacity: " + capacityStr).build());
                    skipped++;
                    continue;
                }

                // seatsPerRow is optional, default 10
                int seatsPerRow = 10;
                if (seatsPerRowStr != null) {
                    try {
                        seatsPerRow = Integer.parseInt(seatsPerRowStr.trim());
                    } catch (NumberFormatException ignored) {}
                }

                toSave.add(Room.builder()
                        .roomName(roomName)
                        .capacity(capacity)
                        .seatsPerRow(seatsPerRow)
                        .building(building)
                        .floor(floor)
                        .day(day)
                        .build());
                imported++;
            }

            roomRepository.saveAll(toSave);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ImportResult.builder().errors(1)
                            .rowErrors(List.of(ImportResult.RowError.builder()
                                    .row(0).reason("Failed to parse file: " + e.getMessage()).build()))
                            .build());
        }

        return ResponseEntity.ok(ImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(rowErrors.size())
                .rowErrors(rowErrors)
                .build());
    }
}
