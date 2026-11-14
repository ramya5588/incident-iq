package com.incidentiq.incident;

import java.time.Duration;
import java.time.Instant;

/**
 * SLA status of one incident: the response deadline (first pick-up) and the
 * resolution deadline. Computed on read, so it is always current.
 */
public record IncidentSla(Clock response, Clock resolution) {

    public enum State {
        /** Not done yet, more than a quarter of the time left. */
        ON_TRACK,
        /** Not done yet, a quarter of the time or less left. */
        AT_RISK,
        /** Past the deadline: still open, or done late. */
        BREACHED,
        /** Done on time. */
        MET,
        /** On hold: the clock is stopped and the deadline moves later while it waits. */
        PAUSED
    }

    /**
     * @param dueAt the deadline
     * @param completedAt when it was done, or null if not yet
     */
    public record Clock(Instant dueAt, Instant completedAt, State state) {

        static Clock of(Instant startedAt, Instant dueAt, Instant completedAt, Instant now) {
            if (completedAt != null) {
                return new Clock(dueAt, completedAt,
                        completedAt.isAfter(dueAt) ? State.BREACHED : State.MET);
            }
            if (!now.isBefore(dueAt)) {
                return new Clock(dueAt, null, State.BREACHED);
            }

            long total = dueAt.toEpochMilli() - startedAt.toEpochMilli();
            long left = dueAt.toEpochMilli() - now.toEpochMilli();
            return new Clock(dueAt, null, left * 4 <= total ? State.AT_RISK : State.ON_TRACK);
        }
    }

    public static IncidentSla of(Incident incident, Instant now) {
        return new IncidentSla(
                Clock.of(incident.getCreatedAt(), incident.getRespondBy(),
                        incident.getFirstRespondedAt(), now),
                resolution(incident, now));
    }

    private static Clock resolution(Incident incident, Instant now) {
        Instant heldSince = incident.getOnHoldSince();
        if (heldSince == null) {
            return Clock.of(incident.getCreatedAt(), incident.getResolveBy(),
                    resolutionCompletedAt(incident), now);
        }
        // Missed before the hold began: a hold doesn't undo that.
        if (!incident.getResolveBy().isAfter(heldSince)) {
            return new Clock(incident.getResolveBy(), null, State.BREACHED);
        }
        // The deadline if work resumed right now.
        Duration held = now.isAfter(heldSince) ? Duration.between(heldSince, now) : Duration.ZERO;
        return new Clock(incident.getResolveBy().plus(held), null, State.PAUSED);
    }

    /** Resolved time; an incident closed without resolving counts as done when closed. */
    private static Instant resolutionCompletedAt(Incident incident) {
        if (incident.getResolvedAt() != null) {
            return incident.getResolvedAt();
        }
        return incident.getStatus() == IncidentStatus.CLOSED ? incident.getUpdatedAt() : null;
    }
}
