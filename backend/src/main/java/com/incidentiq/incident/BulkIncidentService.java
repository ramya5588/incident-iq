package com.incidentiq.incident;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one change to several incidents. Each incident is changed in its
 * own transaction through the normal {@link IncidentService} methods, so the
 * timeline, notifications and SLA work exactly as for a single change, and
 * one incident that can't be changed doesn't stop the others.
 */
@Service
public class BulkIncidentService {

    private final IncidentService incidents;
    private final TransactionTemplate transaction;

    public BulkIncidentService(IncidentService incidents, PlatformTransactionManager transactions) {
        this.incidents = incidents;
        this.transaction = new TransactionTemplate(transactions);
    }

    public BulkUpdateResponse update(BulkUpdateRequest request, UUID actorId) {
        if (!request.changesSomething()) {
            throw new InvalidStatusChangeException("Choose a status, priority or assignee to change.");
        }
        if (request.assigneeId() != null && Boolean.TRUE.equals(request.unassign())) {
            throw InvalidAssigneeException.assignAndUnassign();
        }
        if (request.status() == IncidentStatus.ON_HOLD && request.holdReason() == null) {
            throw new InvalidStatusChangeException("Choose why the incidents are on hold.");
        }

        int updated = 0;
        List<BulkUpdateResponse.Failure> failed = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(request.incidentIds())) {
            try {
                // All of one incident's changes, or none of them.
                transaction.executeWithoutResult(status -> apply(id, request, actorId));
                updated++;
            } catch (IncidentNotFoundException | InvalidStatusChangeException
                    | InvalidAssigneeException | UserNotFoundException exception) {
                failed.add(new BulkUpdateResponse.Failure(id, exception.getMessage()));
            }
        }
        return new BulkUpdateResponse(updated, failed);
    }

    private void apply(UUID id, BulkUpdateRequest request, UUID actorId) {
        if (request.priority() != null) {
            incidents.updateIncident(
                    id, new UpdateIncidentRequest(null, null, request.priority(), null), actorId);
        }
        if (request.changesAssignee()) {
            incidents.updateAssignee(
                    id, new UpdateIncidentAssigneeRequest(request.assigneeId()), actorId);
        }
        if (request.status() != null) {
            incidents.updateStatus(id, new UpdateIncidentStatusRequest(
                    request.status(), request.holdReason(), request.holdNote()), actorId);
        }
    }
}
