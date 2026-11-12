package com.incidentiq.incident;

import java.time.Instant;

/**
 * Dashboard counts computed in the database across all incidents.
 *
 * @param resolvedSince the start of the window used for {@code resolvedInWindow}
 * @param resolvedInWindow incidents whose resolvedAt is at or after
 *        {@code resolvedSince} (includes ones closed after resolving)
 * @param aiTriagedPercentage share of all incidents with AI triage, 0–100, rounded
 * @param slaBreached open or in-progress incidents past a response or
 *        resolution deadline
 */
public record IncidentStatsResponse(
        long total,
        long open,
        long inProgress,
        long resolved,
        long closed,
        Instant resolvedSince,
        long resolvedInWindow,
        long aiTriaged,
        int aiTriagedPercentage,
        long slaBreached) {
}
