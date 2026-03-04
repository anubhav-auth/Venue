package com.anubhavauth.venue.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", fieldErrors
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        return switch (ex.getMessage()) {
            case "ROOM_NOT_FOUND" -> ResponseEntity.notFound().<Map<String, String>>build();
            case "ROOM_HAS_ASSIGNMENTS" -> ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot delete room with existing seat assignments"));
            default -> ResponseEntity.internalServerError()
                    .body(Map.of("error", "An unexpected error occurred"));
        };
    }
}
