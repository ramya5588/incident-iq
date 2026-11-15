package com.incidentiq.incident;

import java.time.Instant;
import java.util.UUID;

public record IncidentEventResponse(
        Long id,
        IncidentEventType type,
        String fromValue,
        String toValue,
        String message,
        UUID actorId,
        String actorName,
        boolean internal,
        Instant createdAt) {

    public static IncidentEventResponse from(IncidentEvent event) {
        return new IncidentEventResponse(
                event.getId(),
                event.getType(),
                event.getFromValue(),
                event.getToValue(),
                event.getMessage(),
                event.getActor() == null ? null : event.getActor().getId(),
                event.getActor() == null ? null : event.getActor().getDisplayName(),
                event.isInternal(),
                event.getCreatedAt());
    }
}
