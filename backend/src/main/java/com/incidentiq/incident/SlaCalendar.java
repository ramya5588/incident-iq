package com.incidentiq.incident;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Which time counts toward an SLA deadline. {@link #ALWAYS} counts every
 * moment; a business-hours calendar counts only working hours on working
 * days that aren't holidays. Deadlines are still exact instants, so overdue
 * checks and alerts work the same either way.
 */
public sealed interface SlaCalendar permits SlaCalendar.Always, SlaCalendar.BusinessHours {

    /** Around the clock. */
    SlaCalendar ALWAYS = new Always();

    /** The moment after {@code amount} of counted time from {@code start}. */
    Instant add(Instant start, Duration amount);

    /** How much counted time lies between two moments (zero if {@code to} isn't later). */
    Duration countedTime(Instant from, Instant to);

    /** True for a business-hours calendar. */
    boolean businessHours();

    final class Always implements SlaCalendar {

        private Always() {
        }

        @Override
        public Instant add(Instant start, Duration amount) {
            return start.plus(amount);
        }

        @Override
        public Duration countedTime(Instant from, Instant to) {
            return to.isAfter(from) ? Duration.between(from, to) : Duration.ZERO;
        }

        @Override
        public boolean businessHours() {
            return false;
        }
    }

    /**
     * Working hours {@code [dayStart, dayEnd)} in {@code zone} on
     * {@code workingDays}, except {@code holidays}. Times are local, so
     * daylight saving changes are handled by the zone rules.
     */
    record BusinessHours(
            ZoneId zone,
            LocalTime dayStart,
            LocalTime dayEnd,
            Set<DayOfWeek> workingDays,
            Set<LocalDate> holidays) implements SlaCalendar {

        /** Looking further than this for working time means the calendar has none. */
        private static final int MAX_DAYS = 3 * 366;

        public BusinessHours {
            if (!dayStart.isBefore(dayEnd)) {
                throw new IllegalArgumentException("The working day must start before it ends");
            }
            if (workingDays.isEmpty()) {
                throw new IllegalArgumentException("At least one working day is needed");
            }
            workingDays = Set.copyOf(workingDays);
            holidays = Set.copyOf(holidays);
        }

        private boolean isWorkingDay(LocalDate date) {
            return workingDays.contains(date.getDayOfWeek()) && !holidays.contains(date);
        }

        private Instant opens(LocalDate date) {
            return ZonedDateTime.of(date, dayStart, zone).toInstant();
        }

        private Instant closes(LocalDate date) {
            return ZonedDateTime.of(date, dayEnd, zone).toInstant();
        }

        @Override
        public Instant add(Instant start, Duration amount) {
            if (amount.isNegative() || amount.isZero()) {
                return start;
            }
            Duration remaining = amount;
            LocalDate date = start.atZone(zone).toLocalDate();

            for (int day = 0; day < MAX_DAYS; day++, date = date.plusDays(1)) {
                if (!isWorkingDay(date)) {
                    continue;
                }
                Instant from = max(start, opens(date));
                Instant until = closes(date);
                if (!from.isBefore(until)) {
                    continue;
                }
                Duration available = Duration.between(from, until);
                if (remaining.compareTo(available) <= 0) {
                    return from.plus(remaining);
                }
                remaining = remaining.minus(available);
            }
            throw new IllegalStateException("No working time found within " + MAX_DAYS + " days");
        }

        @Override
        public Duration countedTime(Instant from, Instant to) {
            if (!to.isAfter(from)) {
                return Duration.ZERO;
            }
            Duration total = Duration.ZERO;
            LocalDate date = from.atZone(zone).toLocalDate();
            LocalDate last = to.atZone(zone).toLocalDate();

            for (; !date.isAfter(last); date = date.plusDays(1)) {
                if (!isWorkingDay(date)) {
                    continue;
                }
                Instant start = max(from, opens(date));
                Instant end = min(to, closes(date));
                if (start.isBefore(end)) {
                    total = total.plus(Duration.between(start, end));
                }
            }
            return total;
        }

        @Override
        public boolean businessHours() {
            return true;
        }

        private static Instant max(Instant a, Instant b) {
            return a.isAfter(b) ? a : b;
        }

        private static Instant min(Instant a, Instant b) {
            return a.isBefore(b) ? a : b;
        }
    }
}
