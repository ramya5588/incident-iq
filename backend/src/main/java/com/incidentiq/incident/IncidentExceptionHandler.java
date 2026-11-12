package com.incidentiq.incident;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IncidentExceptionHandler {

    @ExceptionHandler({
            UserNotFoundException.class,
            IncidentNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(
            RuntimeException exception) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(UnknownActorException.class)
    public ResponseEntity<ApiError> handleUnknownActor(
            UnknownActorException exception) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message) {
    }
}