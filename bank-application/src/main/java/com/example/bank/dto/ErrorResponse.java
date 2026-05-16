package com.example.bank.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(Instant.now(), code, message, path, Map.of());
    }

    public static ErrorResponse validation(String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), "VALIDATION_ERROR", message, path, fieldErrors);
    }
}
