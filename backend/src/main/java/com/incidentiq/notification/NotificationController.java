package com.incidentiq.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.auth.AppUserPrincipal;

import jakarta.validation.Valid;

/** The signed-in user's own notifications. There is no way to see anyone else's. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** The most recent notifications, newest first. */
    @GetMapping
    public List<NotificationResponse> recent(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.recent(me.id());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("unread", service.unreadCount(me.id()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me) {

        service.markRead(id, me.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("marked", service.markAllRead(me.id()));
    }

    /** Which notifications you also get by email. */
    @GetMapping("/email-preferences")
    public EmailPreferences.Response emailPreferences(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.emailPreferences(me.id());
    }

    @PutMapping("/email-preferences")
    public EmailPreferences.Response updateEmailPreferences(
            @AuthenticationPrincipal AppUserPrincipal me,
            @Valid @RequestBody EmailPreferences preferences) {

        return service.updateEmailPreferences(me.id(), preferences);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotificationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", Instant.now(),
                "status", 404,
                "error", "Not Found",
                "message", exception.getMessage()));
    }
}
