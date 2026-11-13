package com.incidentiq.notification;

import java.time.Instant;

import com.incidentiq.incident.Incident;
import com.incidentiq.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One in-app notification for one person. The message is a text snapshot,
 * so it still reads correctly if the incident is renamed later.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private UserAccount recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private UserAccount actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    Notification(
            UserAccount recipient,
            Incident incident,
            NotificationType type,
            String message,
            UserAccount actor,
            Instant createdAt) {

        this.recipient = recipient;
        this.incident = incident;
        this.type = type;
        this.message = message.length() > 500 ? message.substring(0, 499) + "…" : message;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getRecipient() {
        return recipient;
    }

    public Incident getIncident() {
        return incident;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public UserAccount getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
