package com.incidentiq.incident;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports over a date range, computed in the database. "Reported in range"
 * means created between the start of {@code from} and the end of {@code to}
 * in the viewer's time zone. SLA outcomes come from the stored deadlines, so
 * they're right whether or not business hours were in use.
 */
@Service
public class ReportService {

    static final int MAX_DAYS = 366;
    private static final int TOP_CATEGORIES = 5;

    /** When the resolve deadline was met: resolved, or closed without resolving. */
    private static final String COMPLETED_AT =
            "COALESCE(i.resolved_at, CASE WHEN i.status = 'CLOSED' THEN i.updated_at END)";

    private static final String RESPONSE_MET =
            "i.first_responded_at IS NOT NULL AND i.first_responded_at <= i.respond_by";
    private static final String RESPONSE_MISSED =
            "i.first_responded_at > i.respond_by OR (i.first_responded_at IS NULL AND i.respond_by <= :now)";
    private static final String RESOLUTION_MET = COMPLETED_AT + " <= i.resolve_by";
    // Not done and past the deadline, unless it's on hold (paused) and the
    // deadline hadn't passed when the hold started.
    private static final String RESOLUTION_MISSED = COMPLETED_AT + " > i.resolve_by OR ("
            + COMPLETED_AT + " IS NULL AND i.resolve_by <= :now"
            + " AND (i.status <> 'ON_HOLD' OR i.resolve_by <= i.on_hold_since))";

    private final NamedParameterJdbcTemplate jdbc;
    private final IncidentRepository incidents;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, IncidentRepository incidents, Clock clock) {
        this.jdbc = jdbc;
        this.incidents = incidents;
        this.clock = clock;
    }

    /** A checked date range in a time zone, as instants [start, end). */
    record Range(LocalDate from, LocalDate to, ZoneId zone, Instant start, Instant end) {

        static Range of(LocalDate from, LocalDate to, String zoneId) {
            if (from == null || to == null) {
                throw new InvalidReportException("Choose a start and an end date.");
            }
            if (to.isBefore(from)) {
                throw new InvalidReportException("The start date must not be after the end date.");
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
                throw new InvalidReportException("Reports cover at most " + MAX_DAYS + " days.");
            }
            ZoneId zone;
            try {
                zone = ZoneId.of(zoneId == null || zoneId.isBlank() ? "UTC" : zoneId.trim());
            } catch (DateTimeException exception) {
                throw new InvalidReportException("Unknown time zone: " + zoneId);
            }
            // The database reads "+02:00"-style zones with the sign reversed, so
            // only accept named zones (or UTC).
            if (zone instanceof ZoneOffset offset) {
                if (offset.getTotalSeconds() != 0) {
                    throw new InvalidReportException("Use a named time zone, such as Europe/London.");
                }
                zone = ZoneId.of("UTC");
            }
            return new Range(from, to, zone,
                    from.atStartOfDay(zone).toInstant(),
                    to.plusDays(1).atStartOfDay(zone).toInstant());
        }
    }

    private MapSqlParameterSource params(Range range, IncidentPriority priority) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", range.start().atOffset(ZoneOffset.UTC))
                .addValue("end", range.end().atOffset(ZoneOffset.UTC))
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC))
                .addValue("zone", range.zone().getId());
        if (priority != null) {
            params.addValue("priority", priority.name());
        }
        return params;
    }

    private static String priorityFilter(IncidentPriority priority) {
        return priority == null ? "" : " AND i.priority = :priority";
    }

    @Transactional(readOnly = true)
    public ReportSummary summary(LocalDate from, LocalDate to, String zoneId, IncidentPriority priority) {
        Range range = Range.of(from, to, zoneId);
        MapSqlParameterSource params = params(range, priority);
        String where = " WHERE i.created_at >= :start AND i.created_at < :end" + priorityFilter(priority);

        // SLA outcomes of incidents reported in the range, per priority.
        Map<IncidentPriority, ReportSummary.PriorityRow> byPriority = new EnumMap<>(IncidentPriority.class);
        jdbc.query("""
                SELECT i.priority,
                       count(*) AS reported,
                       count(*) FILTER (WHERE %s) AS response_met,
                       count(*) FILTER (WHERE %s) AS response_missed,
                       count(*) FILTER (WHERE %s) AS resolution_met,
                       count(*) FILTER (WHERE %s) AS resolution_missed
                FROM incidents i
                """.formatted(RESPONSE_MET, RESPONSE_MISSED, RESOLUTION_MET, RESOLUTION_MISSED)
                + where + " GROUP BY i.priority", params, (ResultSet row) -> {
                    IncidentPriority rowPriority = IncidentPriority.valueOf(row.getString("priority"));
                    byPriority.put(rowPriority, new ReportSummary.PriorityRow(
                            rowPriority,
                            row.getLong("reported"),
                            new ReportSummary.SlaOutcome(row.getLong("response_met"), row.getLong("response_missed")),
                            new ReportSummary.SlaOutcome(row.getLong("resolution_met"), row.getLong("resolution_missed"))));
                });
        List<ReportSummary.PriorityRow> priorityRows = new ArrayList<>();
        for (IncidentPriority each : IncidentPriority.values()) {
            if (priority == null || priority == each) {
                priorityRows.add(byPriority.getOrDefault(each, ReportSummary.PriorityRow.empty(each)));
            }
        }

        Long medianResponse = jdbc.queryForObject("""
                SELECT round(percentile_cont(0.5) WITHIN GROUP (
                           ORDER BY extract(epoch FROM i.first_responded_at - i.created_at)))
                FROM incidents i
                """ + where + " AND i.first_responded_at IS NOT NULL", params, Long.class);

        String resolvedWhere = " WHERE i.resolved_at >= :start AND i.resolved_at < :end" + priorityFilter(priority);
        Map<String, Object> resolved = jdbc.queryForMap("""
                SELECT count(*) AS resolved,
                       round(percentile_cont(0.5) WITHIN GROUP (
                           ORDER BY extract(epoch FROM i.resolved_at - i.created_at))) AS median
                FROM incidents i
                """ + resolvedWhere, params);

        return new ReportSummary(
                range.from(), range.to(), range.zone().getId(), priority,
                priorityRows.stream().mapToLong(ReportSummary.PriorityRow::reported).sum(),
                ((Number) resolved.get("resolved")).longValue(),
                medianResponse,
                resolved.get("median") == null ? null : ((Number) resolved.get("median")).longValue(),
                ReportSummary.SlaOutcome.sum(priorityRows.stream().map(ReportSummary.PriorityRow::response).toList()),
                ReportSummary.SlaOutcome.sum(priorityRows.stream().map(ReportSummary.PriorityRow::resolution).toList()),
                incidents.count(IncidentSpecifications.overdue(clock.instant())
                        .and(IncidentSpecifications.filter(null, priority, null))),
                daily(range, params, where, resolvedWhere),
                priorityRows,
                topCategories(params, where),
                workload(params, priority),
                satisfaction(params, priority));
    }

    private ReportSummary.Satisfaction satisfaction(MapSqlParameterSource params, IncidentPriority priority) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT count(*) AS ratings,
                       round(avg(i.satisfaction_score), 1) AS average,
                       count(*) FILTER (WHERE i.satisfaction_score >= 4) AS satisfied
                FROM incidents i
                WHERE i.rated_at >= :start AND i.rated_at < :end
                """ + priorityFilter(priority), params);
        Number average = (Number) row.get("average");
        return new ReportSummary.Satisfaction(
                ((Number) row.get("ratings")).longValue(),
                average == null ? null : average.doubleValue(),
                ((Number) row.get("satisfied")).longValue());
    }

    private List<ReportSummary.Day> daily(
            Range range, MapSqlParameterSource params, String where, String resolvedWhere) {
        Map<LocalDate, Long> reported = countByDay("i.created_at", where, params);
        Map<LocalDate, Long> resolved = countByDay("i.resolved_at", resolvedWhere, params);
        List<ReportSummary.Day> days = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            days.add(new ReportSummary.Day(day, reported.getOrDefault(day, 0L), resolved.getOrDefault(day, 0L)));
        }
        return days;
    }

    private Map<LocalDate, Long> countByDay(String column, String where, MapSqlParameterSource params) {
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query("SELECT CAST(" + column + " AT TIME ZONE :zone AS date) AS day, count(*) AS n"
                + " FROM incidents i" + where + " GROUP BY 1", params, (ResultSet row) -> {
                    counts.put(row.getObject("day", LocalDate.class), row.getLong("n"));
                });
        return counts;
    }

    private List<ReportSummary.CategoryCount> topCategories(MapSqlParameterSource params, String where) {
        return jdbc.query("""
                SELECT i.category, count(*) AS n
                FROM incidents i
                """ + where + " GROUP BY i.category ORDER BY n DESC, i.category NULLS LAST LIMIT " + TOP_CATEGORIES,
                params, (row, number) -> new ReportSummary.CategoryCount(row.getString("category"), row.getLong("n")));
    }

    private ReportSummary.Workload workload(MapSqlParameterSource params, IncidentPriority priority) {
        String active = " i.status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD')" + priorityFilter(priority);
        List<ReportSummary.AssigneeLoad> assignees = jdbc.query("""
                SELECT u.id, u.display_name,
                       count(*) FILTER (WHERE i.status = 'OPEN') AS open,
                       count(*) FILTER (WHERE i.status = 'IN_PROGRESS') AS in_progress,
                       count(*) FILTER (WHERE i.status = 'ON_HOLD') AS on_hold
                FROM incidents i JOIN app_users u ON u.id = i.assigned_to
                WHERE""" + active + """
                 GROUP BY u.id, u.display_name
                ORDER BY count(*) DESC, u.display_name
                """, params, (row, number) -> new ReportSummary.AssigneeLoad(
                        row.getObject("id", java.util.UUID.class),
                        row.getString("display_name"),
                        row.getLong("open"), row.getLong("in_progress"), row.getLong("on_hold")));
        Long unassigned = jdbc.queryForObject(
                "SELECT count(*) FROM incidents i WHERE i.assigned_to IS NULL AND" + active, params, Long.class);
        return new ReportSummary.Workload(assignees, unassigned == null ? 0 : unassigned);
    }

    // --- CSV export ---------------------------------------------------------

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Incidents reported in the range, oldest first, as CSV (UTF-8, Excel-friendly). */
    @Transactional(readOnly = true)
    public String csv(LocalDate from, LocalDate to, String zoneId, IncidentPriority priority) {
        Range range = Range.of(from, to, zoneId);
        MapSqlParameterSource params = params(range, priority);
        Instant now = clock.instant();

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Incident,Title,Priority,Status,Category,Reported by,Assignee,Reported,")
                .append("Respond by,First response,Response SLA,Resolve by,Resolved,Resolution SLA,")
                .append("Satisfaction,Feedback\r\n");

        jdbc.query("""
                SELECT i.incident_number, i.title, i.priority, i.status, i.category,
                       r.display_name AS reporter, a.display_name AS assignee,
                       i.created_at, i.respond_by, i.first_responded_at, i.resolve_by,
                       i.resolved_at, i.updated_at, i.on_hold_since,
                       i.satisfaction_score, i.satisfaction_comment
                FROM incidents i
                JOIN app_users r ON r.id = i.created_by
                LEFT JOIN app_users a ON a.id = i.assigned_to
                WHERE i.created_at >= :start AND i.created_at < :end""" + priorityFilter(priority) + """
                 ORDER BY i.created_at, i.incident_number
                """, params, (ResultSet row) -> {
                    appendRow(csv, row, range.zone(), now);
                });
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, ResultSet row, ZoneId zone, Instant now) throws SQLException {
        String status = row.getString("status");
        Instant respondBy = instant(row, "respond_by");
        Instant responded = instant(row, "first_responded_at");
        Instant resolveBy = instant(row, "resolve_by");
        Instant resolved = instant(row, "resolved_at");
        Instant completed = resolved != null ? resolved
                : "CLOSED".equals(status) ? instant(row, "updated_at") : null;
        Instant heldSince = instant(row, "on_hold_since");

        String responseSla = responded != null
                ? (responded.isAfter(respondBy) ? "Missed" : "Met")
                : (!respondBy.isAfter(now) ? "Missed" : "Pending");
        String resolutionSla = completed != null
                ? (completed.isAfter(resolveBy) ? "Missed" : "Met")
                : (!resolveBy.isAfter(now) && (heldSince == null || !resolveBy.isAfter(heldSince))
                        ? "Missed"
                        : "ON_HOLD".equals(status) ? "Paused" : "Pending");

        List<String> cells = List.of(
                "INC-%03d".formatted(row.getLong("incident_number")),
                row.getString("title"),
                row.getString("priority"),
                status,
                nullToEmpty(row.getString("category")),
                row.getString("reporter"),
                nullToEmpty(row.getString("assignee")),
                time(instant(row, "created_at"), zone),
                time(respondBy, zone),
                time(responded, zone),
                responseSla,
                time(resolveBy, zone),
                time(resolved, zone),
                resolutionSla,
                row.getObject("satisfaction_score") == null ? "" : row.getString("satisfaction_score"),
                nullToEmpty(row.getString("satisfaction_comment")));
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(cell(cells.get(i)));
        }
        csv.append("\r\n");
    }

    /**
     * Quotes a value for CSV, and stops spreadsheet apps from running text
     * that starts like a formula (a title such as "=HYPERLINK(...)").
     */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                || text.startsWith("'")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String time(Instant value, ZoneId zone) {
        return value == null ? "" : CSV_TIME.format(value.atZone(zone));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
