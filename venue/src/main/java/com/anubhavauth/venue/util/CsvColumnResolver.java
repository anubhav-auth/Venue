package com.anubhavauth.venue.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "csv")
public class CsvColumnResolver {

    private Map<String, Map<String, String>> student = new HashMap<>();
    private Map<String, Map<String, String>> room = new HashMap<>();
    private java.util.List<String> validBranches = new java.util.ArrayList<>();

    // Getters and setters required for @ConfigurationProperties binding
    public Map<String, Map<String, String>> getStudent() { return student; }
    public void setStudent(Map<String, Map<String, String>> student) { this.student = student; }

    public Map<String, Map<String, String>> getRoom() { return room; }
    public void setRoom(Map<String, Map<String, String>> room) { this.room = room; }

    public java.util.List<String> getValidBranches() { return validBranches; }
    public void setValidBranches(java.util.List<String> validBranches) { this.validBranches = validBranches; }

    /**
     * Resolves header line to a map of fieldKey → columnIndex.
     * Matching is case-insensitive and trimmed.
     *
     * @param headerLine  raw CSV header line (comma-separated)
     * @param columnDefs  map of fieldKey → expectedHeaderName from YAML config
     */
    public Map<String, Integer> resolveHeaders(String headerLine, Map<String, String> columnDefs) {
        String[] headers = headerLine.split(",");
        Map<String, Integer> indexMap = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            for (Map.Entry<String, String> entry : columnDefs.entrySet()) {
                if (entry.getValue().trim().toLowerCase().equals(header)) {
                    indexMap.put(entry.getKey(), i);
                }
            }
        }
        return indexMap;
    }

    /**
     * Retrieves a cell value from a CSV row by fieldKey.
     * Returns null if the column was not found in the header.
     */
    public String getValue(String[] row, Map<String, Integer> indexMap, String fieldKey) {
        Integer index = indexMap.get(fieldKey);
        if (index == null || index >= row.length) return null;
        String val = row[index].trim();
        return val == null || val.isBlank() ? null : val.strip().replace("\r", "").replace("\uFEFF", "");
    }
}
