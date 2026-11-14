package com.incidentiq.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errors from password reset and self-service account changes. */
@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler({
            InvalidResetLinkException.class,
            InvalidConfirmLinkException.class,
            AccountRuleException.class})
    ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PasswordResetUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(PasswordResetUnavailableException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<Map<String, Object>> tooMany(TooManyRequestsException exception) {
        return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
