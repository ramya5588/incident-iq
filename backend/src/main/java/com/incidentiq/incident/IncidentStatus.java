package com.incidentiq.incident;

public enum IncidentStatus {
    OPEN,
    IN_PROGRESS,
    /** Waiting on someone outside the team; the resolution SLA is paused. */
    ON_HOLD,
    RESOLVED,
    CLOSED
}
