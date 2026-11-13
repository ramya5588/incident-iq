package com.incidentiq.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        UUID incidentId,
        String actorName,
        Instant createdAt,
        Instant readAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getIncident().getId(),
                notification.getActor() == null ? null : notification.getActor().getDisplayName(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
