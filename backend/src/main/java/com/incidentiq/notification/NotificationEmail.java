package com.incidentiq.notification;

import java.util.UUID;

/**
 * An email to send for a notification. Holds plain values (not entities)
 * because it's sent after the transaction commits, on another thread.
 */
public record NotificationEmail(
        UUID recipientId,
        String to,
        String recipientName,
        String message,
        UUID incidentId) {
}
