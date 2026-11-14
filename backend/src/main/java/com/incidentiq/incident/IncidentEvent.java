package com.incidentiq.incident;

import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One entry in an incident's activity timeline. Values are stored as text
 * snapshots (e.g. the assignee's name at the time) so history stays readable
 * even if the underlying data changes later.
 */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private IncidentEventType type;

    @Column(name = "from_value", length = 200, updatable = false)
    private String fromValue;

    @Column(name = "to_value", length = 200, updatable = false)
    private String toValue;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private UserAccount actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentEvent() {
    }

    private IncidentEvent(
            Incident incident,
            IncidentEventType type,
            String fromValue,
            String toValue,
            String message,
            UserAccount actor) {

        this.incident = incident;
        this.type = type;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.message = message;
        this.actor = actor;
    }

    public static IncidentEvent created(Incident incident, UserAccount creator) {
        return new IncidentEvent(
                incident, IncidentEventType.CREATED, null, null, null, creator);
    }

    /** {@code actor} may be null when the caller didn't say who they are. */
    public static IncidentEvent statusChanged(
            Incident incident, IncidentStatus from, IncidentStatus to,
            UserAccount actor) {

        return new IncidentEvent(
                incident, IncidentEventType.STATUS_CHANGED,
                from.name(), to.name(), null, actor);
    }

    /** Status change with a note, e.g. why it was put on hold. */
    public static IncidentEvent statusChanged(
            Incident incident, IncidentStatus from, IncidentStatus to,
            String message, UserAccount actor) {

        return new IncidentEvent(
                incident, IncidentEventType.STATUS_CHANGED,
                from.name(), to.name(), message, actor);
    }

    /** {@code actor} may be null when the caller didn't say who they are. */
    public static IncidentEvent assigneeChanged(
            Incident incident, UserAccount from, UserAccount to,
            UserAccount actor) {

        return new IncidentEvent(
                incident, IncidentEventType.ASSIGNEE_CHANGED,
                from == null ? null : from.getDisplayName(),
                to == null ? null : to.getDisplayName(),
                null, actor);
    }

    /** {@code actor} is who ran triage, or null if unknown. */
    public static IncidentEvent aiTriaged(
            Incident incident, String summary, UserAccount actor) {

        return new IncidentEvent(
                incident, IncidentEventType.AI_TRIAGED, null, null, summary, actor);
    }

    /** Title, priority or category edit: stores old and new values. */
    public static IncidentEvent fieldChanged(
            Incident incident, IncidentEventType type,
            String from, String to, UserAccount actor) {

        return new IncidentEvent(incident, type, from, to, null, actor);
    }

    /** Descriptions are too long to snapshot, so only the fact is kept. */
    public static IncidentEvent descriptionEdited(
            Incident incident, UserAccount actor) {

        return new IncidentEvent(
                incident, IncidentEventType.DESCRIPTION_EDITED,
                null, null, null, actor);
    }

    /** Attachment added or removed; stores the file name (cut to fit). */
    public static IncidentEvent attachment(
            Incident incident, IncidentEventType type, String fileName, UserAccount actor) {

        String name = fileName.length() > 200 ? fileName.substring(0, 199) + "…" : fileName;
        return new IncidentEvent(incident, type, null, name, null, actor);
    }

    public static IncidentEvent comment(
            Incident incident, UserAccount author, String body) {

        return new IncidentEvent(
                incident, IncidentEventType.COMMENT, null, null, body, author);
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public IncidentEventType getType() {
        return type;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
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
}
