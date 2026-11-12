package com.incidentiq.incident;

import java.util.UUID;

/**
 * Assigns an incident to a user. A null {@code assigneeId} unassigns it.
 */
public record UpdateIncidentAssigneeRequest(
        UUID assigneeId) {
}
