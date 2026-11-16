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
        String assignedToName,
        String aiSummary,
        IncidentPriority aiSuggestedPriority,
        String aiSuggestedCategory,
        String aiReasoning,
        Instant aiTriagedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        HoldReason holdReason,
        String holdNote,
        Instant onHoldSince,
        Integer satisfactionScore,
        String satisfactionComment,
        Instant ratedAt,
        UUID duplicateOfId,
        Long duplicateOfNumber,
        String duplicateOfTitle,
        IncidentSla sla) {

    public static IncidentResponse from(Incident incident, IncidentSla sla) {
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
                incident.getAssignedTo() == null
                        ? null
                        : incident.getAssignedTo().getDisplayName(),
                incident.getAiSummary(),
                incident.getAiSuggestedPriority(),
                incident.getAiSuggestedCategory(),
                incident.getAiReasoning(),
                incident.getAiTriagedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getResolvedAt(),
                incident.getHoldReason(),
                incident.getHoldNote(),
                incident.getOnHoldSince(),
                incident.getSatisfactionScore(),
                incident.getSatisfactionComment(),
                incident.getRatedAt(),
                incident.getDuplicateOf() == null ? null : incident.getDuplicateOf().getId(),
                incident.getDuplicateOf() == null ? null : incident.getDuplicateOf().getIncidentNumber(),
                incident.getDuplicateOf() == null ? null : incident.getDuplicateOf().getTitle(),
                sla);
    }
}