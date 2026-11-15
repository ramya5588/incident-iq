package com.incidentiq.incident;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The same change for several incidents. Fields left out are not changed.
 *
 * @param assigneeId who to assign them to; see {@code unassign}
 * @param unassign true to clear the assignee (then {@code assigneeId} must be empty)
 * @param holdReason required when {@code status} is ON_HOLD
 */
public record BulkUpdateRequest(
        @NotEmpty(message = "Choose at least one incident")
        @Size(max = BulkUpdateRequest.MAX_INCIDENTS,
                message = "Change at most " + BulkUpdateRequest.MAX_INCIDENTS + " incidents at a time")
        List<@NotNull UUID> incidentIds,
        IncidentStatus status,
        HoldReason holdReason,
        @Size(max = 200, message = "Note must be at most 200 characters") String holdNote,
        IncidentPriority priority,
        UUID assigneeId,
        Boolean unassign) {

    public static final int MAX_INCIDENTS = 100;

    boolean changesAssignee() {
        return assigneeId != null || Boolean.TRUE.equals(unassign);
    }

    boolean changesSomething() {
        return status != null || priority != null || changesAssignee();
    }
}
