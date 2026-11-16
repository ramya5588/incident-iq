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
            IncidentNotFoundException.class,
            AttachmentNotFoundException.class,
            com.incidentiq.reply.SavedReplyNotFoundException.class
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

    @ExceptionHandler({
            InvalidAssigneeException.class,
            InvalidStatusChangeException.class,
            InvalidBusinessHoursException.class,
            InvalidReportException.class,
            InvalidAttachmentException.class,
            InvalidCommentException.class,
            InvalidMergeException.class})
    public ResponseEntity<ApiError> handleBadRequest(
            RuntimeException exception) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                exception.getMessage());

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler({
            AttachmentForbiddenException.class,
            InternalNoteForbiddenException.class,
            RatingNotAllowedException.class,
            com.incidentiq.reply.SavedReplyForbiddenException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                Instant.now(), HttpStatus.FORBIDDEN.value(), "Forbidden", exception.getMessage()));
    }

    /** Over the upload size limit (rejected before reaching a controller). */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ApiError(
                Instant.now(), HttpStatus.CONTENT_TOO_LARGE.value(), "Content Too Large",
                "Files can be at most 5 MB."));
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message) {
    }
}