package com.incidentiq.incident;

import java.util.List;
import java.util.UUID;

/** How many incidents were changed, and why any weren't. */
public record BulkUpdateResponse(int updated, List<Failure> failed) {

    public record Failure(UUID incidentId, String message) {
    }
}
