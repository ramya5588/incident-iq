package com.incidentiq.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PasswordResetExceptionHandler {

    @ExceptionHandler(InvalidResetLinkException.class)
    ResponseEntity<Map<String, Object>> invalidLink(InvalidResetLinkException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PasswordResetUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(PasswordResetUnavailableException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
