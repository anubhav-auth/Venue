package com.anubhavauth.venue.controller;

import com.anubhavauth.venue.dto.ImportResult;
import com.anubhavauth.venue.entity.Room;
import com.anubhavauth.venue.repository.RoomRepository;
import com.anubhavauth.venue.service.CsvImportService;
import com.anubhavauth.venue.util.CsvColumnResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class RoomCsvController {

    private final RoomRepository roomRepository;
    private final CsvColumnResolver csvColumnResolver;
    private final CsvImportService csvImportService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("uploadExecutor")
    private Executor uploadExecutor;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadRooms(
            @RequestParam("file") MultipartFile file) throws Exception {

        String jobId = UUID.randomUUID().toString();
        byte[] bytes = file.getBytes();

        stringRedisTemplate.opsForValue()
                .set("upload:job:" + jobId, "PROCESSING", 2, TimeUnit.HOURS);

        CompletableFuture.runAsync(() -> processUpload(jobId, bytes), uploadExecutor);

        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/upload/status/{jobId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable String jobId) {
        String val = stringRedisTemplate.opsForValue().get("upload:job:" + jobId);
        if (val == null) return ResponseEntity.notFound().build();
        if ("PROCESSING".equals(val)) return ResponseEntity.ok(Map.of("status", "PROCESSING"));
        if (val.startsWith("ERROR:"))
            return ResponseEntity.ok(Map.of("status", "ERROR", "message", val.substring(6)));
        try {
            ImportResult result = objectMapper.readValue(val, ImportResult.class);
            return ResponseEntity.ok(Map.of("status", "DONE", "result", result));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "ERROR", "message", "Failed to read result"));
        }
    }

    private void processUpload(String jobId, byte[] bytes) {
        List<ImportResult.RowError> rowErrors = new ArrayList<>();
        List<Room> toSave = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes)))) {

            String headerLine = reader.readLine();
            if (headerLine == null) { storeError(jobId, "Empty file"); return; }

            Map<String, String> columnDefs = csvColumnResolver.getRoom().get("columns");
            Map<String, Integer> indexMap = csvColumnResolver.resolveHeaders(headerLine, columnDefs);

            for (String required : List.of("roomName", "capacity", "building", "floor", "day")) {
                if (!indexMap.containsKey(required)) {
                    storeError(jobId, "Missing required column: " + required);
                    return;
                }
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);

                String roomName      = csvColumnResolver.getValue(cols, indexMap, "roomName");
                String capacityStr   = csvColumnResolver.getValue(cols, indexMap, "capacity");
                String building      = csvColumnResolver.getValue(cols, indexMap, "building");
                String floor         = csvColumnResolver.getValue(cols, indexMap, "floor");
                String day           = csvColumnResolver.getValue(cols, indexMap, "day");
                String seatsPerRowStr = csvColumnResolver.getValue(cols, indexMap, "seatsPerRow");

                if (roomName == null || capacityStr == null || building == null
                        || floor == null || day == null) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum).reason("Missing required field(s)").build());
                    skipped++; continue;
                }
                if (!day.equals("day1") && !day.equals("day2")) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                            .reason("Invalid day: " + day).build());
                    skipped++; continue;
                }

                int capacity;
                try {
                    capacity = Integer.parseInt(capacityStr.trim());
                    if (capacity <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    rowErrors.add(ImportResult.RowError.builder().row(rowNum)
                            .reason("Invalid capacity: " + capacityStr).build());
                    skipped++; continue;
                }

                int seatsPerRow = 10;
                if (seatsPerRowStr != null) {
                    try { seatsPerRow = Integer.parseInt(seatsPerRowStr.trim()); }
                    catch (NumberFormatException ignored) {}
                }

                toSave.add(Room.builder()
                        .roomName(roomName)
                        .capacity(capacity)
                        .seatsPerRow(seatsPerRow)
                        .building(building)
                        .floor(floor)
                        .day(day)
                        .build());
            }

            csvImportService.saveRooms(toSave);

            ImportResult result = ImportResult.builder()
                    .imported(toSave.size())
                    .skipped(skipped)
                    .errors(rowErrors.size())
                    .rowErrors(rowErrors)
                    .build();

            stringRedisTemplate.opsForValue()
                    .set("upload:job:" + jobId, objectMapper.writeValueAsString(result), 2, TimeUnit.HOURS);

        } catch (Exception e) {
            storeError(jobId, e.getMessage());
        }
    }

    private void storeError(String jobId, String msg) {
        stringRedisTemplate.opsForValue()
                .set("upload:job:" + jobId, "ERROR:" + msg, 2, TimeUnit.HOURS);
    }
}
