package com.incidentiq.incident;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Report for a date range. Times are in seconds. {@code medianResponseSeconds}
 * covers incidents reported in the range that have been picked up;
 * {@code medianResolveSeconds} covers incidents resolved in the range. Both
 * are calendar time, not business hours. SLA outcomes count incidents
 * reported in the range whose deadline is decided (met, or missed); ones
 * still running aren't counted yet.
 */
public record ReportSummary(
        LocalDate from,
        LocalDate to,
        String timeZone,
        IncidentPriority priority,
        long reported,
        long resolved,
        Long medianResponseSeconds,
        Long medianResolveSeconds,
        SlaOutcome responseSla,
        SlaOutcome resolutionSla,
        long overdueNow,
        List<Day> daily,
        List<PriorityRow> byPriority,
        List<CategoryCount> topCategories,
        Workload workload,
        Satisfaction satisfaction) {

    /**
     * Ratings given in the range (by when they were given). {@code average}
     * is null with no ratings; {@code satisfied} counts 4s and 5s.
     */
    public record Satisfaction(long ratings, Double average, long satisfied) {
    }

    public record SlaOutcome(long met, long missed) {

        static SlaOutcome sum(List<SlaOutcome> outcomes) {
            return new SlaOutcome(
                    outcomes.stream().mapToLong(SlaOutcome::met).sum(),
                    outcomes.stream().mapToLong(SlaOutcome::missed).sum());
        }
    }

    /** How many incidents were still open (not resolved or closed) at the end of a day. */
    public record OpenDay(LocalDate date, long open) {
    }

    /** Reported and resolved on one day, in the report's time zone. */
    public record Day(LocalDate date, long reported, long resolved) {
    }

    public record PriorityRow(
            IncidentPriority priority, long reported, SlaOutcome response, SlaOutcome resolution) {

        static PriorityRow empty(IncidentPriority priority) {
            return new PriorityRow(priority, 0, new SlaOutcome(0, 0), new SlaOutcome(0, 0));
        }
    }

    /** {@code category} is null for incidents without one. */
    public record CategoryCount(String category, long count) {
    }

    /** Current open work (not limited to the date range). */
    public record Workload(List<AssigneeLoad> assignees, long unassigned) {
    }

    public record AssigneeLoad(UUID userId, String name, long open, long inProgress, long onHold) {
    }
}
