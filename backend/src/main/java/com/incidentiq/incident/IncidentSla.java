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
     * @param businessHours true when only business hours count toward it
     * @param countedSecondsLeft counted time left (business hours, or all
     *        time), or null once it's done or missed
     */
    public record Clock(
            Instant dueAt,
            Instant completedAt,
            State state,
            boolean businessHours,
            Long countedSecondsLeft) {

        static Clock of(
                Instant startedAt, Instant dueAt, Instant completedAt, Instant now,
                SlaCalendar calendar) {
            boolean business = calendar.businessHours();
            if (completedAt != null) {
                return new Clock(dueAt, completedAt,
                        completedAt.isAfter(dueAt) ? State.BREACHED : State.MET, business, null);
            }
            if (!now.isBefore(dueAt)) {
                return new Clock(dueAt, null, State.BREACHED, business, null);
            }

            // At risk once a quarter or less of the counted time is left.
            Duration total = calendar.countedTime(startedAt, dueAt);
            Duration left = calendar.countedTime(now, dueAt);
            State state = left.multipliedBy(4).compareTo(total) <= 0 ? State.AT_RISK : State.ON_TRACK;
            return new Clock(dueAt, null, state, business, left.toSeconds());
        }
    }

    public static IncidentSla of(Incident incident, Instant now, SlaCalendar calendar) {
        return new IncidentSla(
                Clock.of(incident.getCreatedAt(), incident.getRespondBy(),
                        incident.getFirstRespondedAt(), now, calendar),
                resolution(incident, now, calendar));
    }

    private static Clock resolution(Incident incident, Instant now, SlaCalendar calendar) {
        Instant heldSince = incident.getOnHoldSince();
        boolean business = calendar.businessHours();
        if (heldSince == null) {
            return Clock.of(incident.getCreatedAt(), incident.getResolveBy(),
                    resolutionCompletedAt(incident), now, calendar);
        }
        // Missed before the hold began: a hold doesn't undo that.
        if (!incident.getResolveBy().isAfter(heldSince)) {
            return new Clock(incident.getResolveBy(), null, State.BREACHED, business, null);
        }
        // The deadline if work resumed right now; the time left doesn't change while paused.
        Duration held = calendar.countedTime(heldSince, now);
        return new Clock(calendar.add(incident.getResolveBy(), held), null, State.PAUSED,
                business, calendar.countedTime(heldSince, incident.getResolveBy()).toSeconds());
    }

    /** Resolved time; an incident closed without resolving counts as done when closed. */
    private static Instant resolutionCompletedAt(Incident incident) {
        if (incident.getResolvedAt() != null) {
            return incident.getResolvedAt();
        }
        return incident.getStatus() == IncidentStatus.CLOSED ? incident.getUpdatedAt() : null;
    }
}
