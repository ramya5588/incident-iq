package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Business-hours arithmetic. January 2026: the 5th is a Monday, the 9th a
 * Friday. London switches to summer time on 29 March 2026 (01:00 → 02:00).
 */
class SlaCalendarTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private static SlaCalendar nineToFive(LocalDate... holidays) {
        return new SlaCalendar.BusinessHours(
                LONDON, LocalTime.of(9, 0), LocalTime.of(17, 0), WEEKDAYS, Set.of(holidays));
    }

    private static Instant london(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(LONDON).toInstant();
    }

    @Test
    void addsWithinTheSameDay() {
        assertThat(nineToFive().add(london("2026-01-05T10:00"), Duration.ofHours(2)))
                .isEqualTo(london("2026-01-05T12:00"));
    }

    @Test
    void endingExactlyAtClosingTimeStaysThatDay() {
        assertThat(nineToFive().add(london("2026-01-05T09:00"), Duration.ofHours(8)))
                .isEqualTo(london("2026-01-05T17:00"));
    }

    @Test
    void carriesOverNightAndOverTheWeekend() {
        SlaCalendar calendar = nineToFive();
        assertThat(calendar.add(london("2026-01-05T16:00"), Duration.ofHours(2)))
                .isEqualTo(london("2026-01-06T10:00"));
        // Friday 16:30 + 1h: 30m on Friday, 30m on Monday.
        assertThat(calendar.add(london("2026-01-09T16:30"), Duration.ofHours(1)))
                .isEqualTo(london("2026-01-12T09:30"));
    }

    @Test
    void timeOutsideHoursStartsCountingAtOpening() {
        SlaCalendar calendar = nineToFive();
        assertThat(calendar.add(london("2026-01-05T07:00"), Duration.ofMinutes(15)))
                .isEqualTo(london("2026-01-05T09:15"));
        assertThat(calendar.add(london("2026-01-05T20:00"), Duration.ofMinutes(15)))
                .isEqualTo(london("2026-01-06T09:15"));
        assertThat(calendar.add(london("2026-01-10T12:00"), Duration.ofHours(1)))
                .as("reported on Saturday")
                .isEqualTo(london("2026-01-12T10:00"));
    }

    @Test
    void skipsHolidays() {
        SlaCalendar calendar = nineToFive(LocalDate.parse("2026-01-12"));
        assertThat(calendar.add(london("2026-01-09T16:30"), Duration.ofHours(1)))
                .isEqualTo(london("2026-01-13T09:30"));
    }

    @Test
    void longTargetsSpanSeveralWeeks() {
        // 3 days = 72 working hours = 9 working days of 8h.
        assertThat(nineToFive().add(london("2026-01-05T09:00"), Duration.ofDays(3)))
                .isEqualTo(london("2026-01-15T17:00"));
    }

    @Test
    void countsOnlyWorkingTime() {
        SlaCalendar calendar = nineToFive(LocalDate.parse("2026-01-12"));
        // Fri 16:45 → Tue 10:00 with Monday a holiday: 15m + 1h.
        assertThat(calendar.countedTime(london("2026-01-09T16:45"), london("2026-01-13T10:00")))
                .isEqualTo(Duration.ofMinutes(75));
        assertThat(calendar.countedTime(london("2026-01-10T08:00"), london("2026-01-11T20:00")))
                .as("a weekend")
                .isZero();
        assertThat(calendar.countedTime(london("2026-01-06T10:00"), london("2026-01-05T10:00")))
                .as("backwards")
                .isZero();
    }

    @Test
    void addingThenCountingRoundTrips() {
        SlaCalendar calendar = nineToFive(LocalDate.parse("2026-01-12"));
        Instant start = london("2026-01-08T13:20");
        Duration amount = Duration.ofHours(19).plusMinutes(7);
        assertThat(calendar.countedTime(start, calendar.add(start, amount))).isEqualTo(amount);
    }

    @Test
    void usesLocalHoursAcrossDaylightSavingChanges() {
        SlaCalendar calendar = nineToFive();
        // Friday 27 March (GMT) 16:00 + 2h → Monday 30 March 10:00 BST.
        Instant due = calendar.add(london("2026-03-27T16:00"), Duration.ofHours(2));
        assertThat(due).isEqualTo(london("2026-03-30T10:00"));
        assertThat(due).isEqualTo(Instant.parse("2026-03-30T09:00:00Z"));
    }

    @Test
    void aroundTheClockCountsEverything() {
        Instant start = Instant.parse("2026-01-09T16:30:00Z");
        assertThat(SlaCalendar.ALWAYS.add(start, Duration.ofHours(1)))
                .isEqualTo(Instant.parse("2026-01-09T17:30:00Z"));
        assertThat(SlaCalendar.ALWAYS.countedTime(start, start.plusSeconds(90)))
                .isEqualTo(Duration.ofSeconds(90));
        assertThat(SlaCalendar.ALWAYS.businessHours()).isFalse();
    }

    @Test
    void rejectsCalendarsWithNoWorkingTime() {
        assertThatThrownBy(() -> new SlaCalendar.BusinessHours(
                LONDON, LocalTime.of(17, 0), LocalTime.of(9, 0), WEEKDAYS, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlaCalendar.BusinessHours(
                LONDON, LocalTime.of(9, 0), LocalTime.of(17, 0), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
