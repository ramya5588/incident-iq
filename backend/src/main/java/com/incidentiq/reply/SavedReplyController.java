package com.incidentiq.reply;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.auth.AppUserPrincipal;

import jakarta.validation.Valid;

/** Agents and admins only (SecurityConfig). */
@RestController
@RequestMapping("/api/saved-replies")
public class SavedReplyController {

    private final SavedReplyService service;

    public SavedReplyController(SavedReplyService service) {
        this.service = service;
    }

    /** Shared replies first, then your personal ones. */
    @GetMapping
    public List<SavedReplyResponse> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.list(me);
    }

    @PostMapping
    public ResponseEntity<SavedReplyResponse> create(
            @Valid @RequestBody SavedReplyRequest request,
            @AuthenticationPrincipal AppUserPrincipal me) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, me));
    }

    @PutMapping("/{id}")
    public SavedReplyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody SavedReplyRequest request,
            @AuthenticationPrincipal AppUserPrincipal me) {

        return service.update(id, request, me);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal me) {

        service.delete(id, me);
        return ResponseEntity.noContent().build();
    }
}
