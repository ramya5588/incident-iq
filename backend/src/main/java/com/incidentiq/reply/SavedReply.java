package com.incidentiq.reply;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reusable text agents insert into comments. Shared replies ({@code ownerId}
 * null) are for the whole team and managed by admins; personal ones belong to
 * one agent or admin.
 */
@Entity
@Table(name = "saved_replies")
public class SavedReply {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedReply() {
    }

    public SavedReply(String title, String body, UUID ownerId, Instant now) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.ownerId = ownerId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String title, String body, Instant now) {
        this.title = title;
        this.body = body;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isShared() {
        return ownerId == null;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
