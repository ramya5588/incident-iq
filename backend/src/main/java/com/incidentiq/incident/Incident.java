package com.incidentiq.incident;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.incidentiq.user.UserAccount;
import com.incidentiq.ai.TriageResult;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_number", insertable = false, updatable = false)
    private Long incidentNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentPriority priority = IncidentPriority.MEDIUM;

    @Column(length = 100)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private UserAccount assignedTo;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_priority", length = 20)
    private IncidentPriority aiSuggestedPriority;
    @Column(name = "ai_suggested_category", length = 100)
    private String aiSuggestedCategory;

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    @Column(name = "ai_triaged_at")
    private Instant aiTriagedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** SLA: someone should pick it up (assign / leave Open) by this time. */
    @Column(name = "respond_by", nullable = false)
    private Instant respondBy;

    /** SLA: it should be resolved by this time. */
    @Column(name = "resolve_by", nullable = false)
    private Instant resolveBy;

    /** When it was first assigned or moved out of Open; never cleared. */
    @Column(name = "first_responded_at")
    private Instant firstRespondedAt;

    /** When a missed response deadline was announced (so it's announced once). */
    @Column(name = "response_breach_notified_at")
    private Instant responseBreachNotifiedAt;

    /** When a missed resolution deadline was announced. */
    @Column(name = "resolution_breach_notified_at")
    private Instant resolutionBreachNotifiedAt;

    /** Why it's on hold; set only while the status is ON_HOLD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason", length = 30)
    private HoldReason holdReason;

    @Column(name = "hold_note", length = 200)
    private String holdNote;

    /** When the current hold started; set only while the status is ON_HOLD. */
    @Column(name = "on_hold_since")
    private Instant onHoldSince;

    /** Time spent in earlier, finished holds; added to the resolution deadline. */
    @Column(name = "sla_paused_micros", nullable = false)
    private long slaPausedMicros;

    protected Incident() {
        // Required by JPA
    }

    public Incident(
            String title,
            String description,
            IncidentPriority priority,
            String category,
            UserAccount createdBy) {

        this.title = title;
        this.description = description;
        this.priority = priority;
        this.category = category;
        this.createdBy = createdBy;
    }

    /**
     * Sets the reported time and the SLA deadlines. Call once, before the
     * incident is first saved.
     */
    public void startSla(Instant reportedAt, SlaProperties.Target target) {
        this.createdAt = reportedAt;
        this.updatedAt = reportedAt;
        applyTarget(target);
    }

    private void applyTarget(SlaProperties.Target target) {
        this.respondBy = createdAt.plus(target.respond());
        this.resolveBy = createdAt.plus(target.resolve()).plus(slaPausedMicros, ChronoUnit.MICROS);

        // A deadline moved past the time its breach was announced can be
        // breached (and announced) again.
        if (responseBreachNotifiedAt != null && respondBy.isAfter(responseBreachNotifiedAt)) {
            responseBreachNotifiedAt = null;
        }
        if (resolutionBreachNotifiedAt != null && resolveBy.isAfter(resolutionBreachNotifiedAt)) {
            resolutionBreachNotifiedAt = null;
        }
    }

    public void markResponseBreachNotified(Instant now) {
        this.responseBreachNotifiedAt = now;
    }

    public void markResolutionBreachNotified(Instant now) {
        this.resolutionBreachNotifiedAt = now;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public void applyAiTriage(TriageResult result, Instant now) {
        this.aiSummary = result.summary();
        this.aiSuggestedCategory = result.category();
        this.aiSuggestedPriority = result.suggestedPriority();
        this.aiReasoning = result.reasoning();
        this.aiTriagedAt = now;
    }

    public void rename(String title) {
        this.title = title;
    }

    public void describe(String description) {
        this.description = description;
    }

    /** Deadlines are recalculated from when the incident was reported. */
    public void changePriority(IncidentPriority priority, SlaProperties.Target target) {
        this.priority = priority;
        applyTarget(target);
    }

    /** {@code null} clears the category. */
    public void changeCategory(String category) {
        this.category = category;
    }

    public void assignTo(UserAccount assignee, Instant now) {
        this.assignedTo = assignee;
        if (assignee != null) {
            markResponded(now);
        }
    }

    /** Changes status; use {@link #putOnHold} to put it on hold. */
    public void updateStatus(IncidentStatus status, Instant now) {
        if (status == IncidentStatus.ON_HOLD) {
            throw new IllegalArgumentException("Use putOnHold to put an incident on hold");
        }
        endHold(now);
        this.status = status;

        if (status != IncidentStatus.OPEN) {
            markResponded(now);
        }

        if (status == IncidentStatus.RESOLVED) {
            this.resolvedAt = now;
        } else if (status == IncidentStatus.OPEN
                || status == IncidentStatus.IN_PROGRESS) {
            this.resolvedAt = null;
        }
    }

    /**
     * Pauses the resolution clock. If it's already on hold, only the reason
     * and note change (the hold keeps its start time).
     */
    public void putOnHold(HoldReason reason, String note, Instant now) {
        if (status != IncidentStatus.ON_HOLD) {
            this.status = IncidentStatus.ON_HOLD;
            this.onHoldSince = now;
            this.resolvedAt = null;
            markResponded(now);
        }
        this.holdReason = reason;
        this.holdNote = note;
    }

    /** Ends a hold, if any: the time on hold moves the resolution deadline later. */
    private void endHold(Instant now) {
        if (onHoldSince == null) {
            return;
        }
        long paused = Math.max(0, ChronoUnit.MICROS.between(onHoldSince, now));
        slaPausedMicros += paused;
        resolveBy = resolveBy.plus(paused, ChronoUnit.MICROS);
        if (resolutionBreachNotifiedAt != null && resolveBy.isAfter(resolutionBreachNotifiedAt)) {
            resolutionBreachNotifiedAt = null;
        }
        onHoldSince = null;
        holdReason = null;
        holdNote = null;
    }

    private void markResponded(Instant now) {
        if (firstRespondedAt == null) {
            firstRespondedAt = now;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getIncidentNumber() {
        return incidentNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public IncidentPriority getPriority() {
        return priority;
    }

    public String getCategory() {
        return category;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public UserAccount getAssignedTo() {
        return assignedTo;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public IncidentPriority getAiSuggestedPriority() {
        return aiSuggestedPriority;
    }

    public String getAiSuggestedCategory() {
        return aiSuggestedCategory;
    }

    public String getAiReasoning() {
        return aiReasoning;
    }

    public Instant getAiTriagedAt() {
        return aiTriagedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getRespondBy() {
        return respondBy;
    }

    public Instant getResolveBy() {
        return resolveBy;
    }

    public Instant getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public Instant getResponseBreachNotifiedAt() {
        return responseBreachNotifiedAt;
    }

    public Instant getResolutionBreachNotifiedAt() {
        return resolutionBreachNotifiedAt;
    }

    public HoldReason getHoldReason() {
        return holdReason;
    }

    public String getHoldNote() {
        return holdNote;
    }

    public Instant getOnHoldSince() {
        return onHoldSince;
    }

    public long getSlaPausedMicros() {
        return slaPausedMicros;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}