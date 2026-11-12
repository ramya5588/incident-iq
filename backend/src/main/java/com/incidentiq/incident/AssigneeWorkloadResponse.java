package com.incidentiq.incident;

import java.util.UUID;

/**
 * Number of active (open or in-progress) incidents assigned to a user.
 * Users with no active incidents are omitted.
 */
public record AssigneeWorkloadResponse(
        UUID userId,
        long activeCount) {
}
