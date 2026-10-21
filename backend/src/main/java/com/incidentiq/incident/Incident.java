package com.incidentiq.incident;

import java.time.Instant;
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

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public void applyAiTriage(TriageResult result) {
        this.aiSummary = result.summary();
        this.aiSuggestedCategory = result.category();
        this.aiSuggestedPriority = result.suggestedPriority();
        this.aiReasoning = result.reasoning();
        this.aiTriagedAt = Instant.now();
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

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}