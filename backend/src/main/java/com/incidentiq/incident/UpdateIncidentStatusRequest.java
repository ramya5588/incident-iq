package com.incidentiq.incident;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param holdReason required when {@code status} is ON_HOLD, ignored otherwise
 * @param holdNote optional detail shown with the reason
 */
public record UpdateIncidentStatusRequest(
        @NotNull IncidentStatus status,
        HoldReason holdReason,
        @Size(max = 200, message = "Note must be at most 200 characters") String holdNote) {

    public UpdateIncidentStatusRequest {
        holdNote = holdNote == null || holdNote.isBlank() ? null : holdNote.trim();
    }

    public UpdateIncidentStatusRequest(IncidentStatus status) {
        this(status, null, null);
    }
}
