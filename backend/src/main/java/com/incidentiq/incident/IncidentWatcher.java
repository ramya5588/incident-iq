package com.incidentiq.incident;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Someone following an incident: they get its updates (see NotificationService). */
@Entity
@Table(name = "incident_watchers")
public class IncidentWatcher {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentWatcher() {
    }

    public IncidentWatcher(UUID incidentId, UUID userId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
