package com.incidentiq.incident;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        Long incidentNumber,
        String title,
        String description,
        IncidentStatus status,
        IncidentPriority priority,
        String category,
        UUID createdById,
        UUID assignedToId,
        String aiSummary,
        IncidentPriority aiSuggestedPriority,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getStatus(),
                incident.getPriority(),
                incident.getCategory(),
                incident.getCreatedBy().getId(),
                incident.getAssignedTo() == null
                        ? null
                        : incident.getAssignedTo().getId(),
                incident.getAiSummary(),
                incident.getAiSuggestedPriority(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getResolvedAt());
    }
}