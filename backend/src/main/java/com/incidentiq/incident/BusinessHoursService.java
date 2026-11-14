package com.incidentiq.incident;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business hours for SLA deadlines. Changes apply to deadlines set from then
 * on (new incidents, priority changes, coming off hold); existing deadlines
 * stay as they are, like SLA target changes.
 */
@Service
public class BusinessHoursService {

    private static final Logger log = LoggerFactory.getLogger(BusinessHoursService.class);

    private final BusinessHoursRepository settings;
    private final SlaHolidayRepository holidays;
    private final Clock clock;

    public BusinessHoursService(
            BusinessHoursRepository settings, SlaHolidayRepository holidays, Clock clock) {
        this.settings = settings;
        this.holidays = holidays;
        this.clock = clock;
    }

    private BusinessHoursSettings row() {
        return settings.findById(BusinessHoursSettings.ID)
                .orElseThrow(() -> new IllegalStateException("Business-hours settings row is missing"));
    }

    /** The calendar for each priority, as of now. */
    @Transactional(readOnly = true)
    public SlaCalendars calendars() {
        BusinessHoursSettings row = row();
        if (!row.isEnabled()) {
            return SlaCalendars.ALWAYS;
        }
        Set<LocalDate> days = holidays.findAll().stream()
                .map(SlaHoliday::getDay)
                .collect(Collectors.toSet());
        SlaCalendar calendar = new SlaCalendar.BusinessHours(
                ZoneId.of(row.getTimeZone()), row.getDayStart(), row.getDayEnd(),
                parse(row.getWorkingDays(), DayOfWeek.class), days);
        return new SlaCalendars(calendar, parse(row.getPriorities(), IncidentPriority.class));
    }

    @Transactional(readOnly = true)
    public BusinessHoursDto get() {
        BusinessHoursSettings row = row();
        return new BusinessHoursDto(
                row.isEnabled(),
                row.getTimeZone(),
                row.getDayStart().toString(),
                row.getDayEnd().toString(),
                List.copyOf(parse(row.getWorkingDays(), DayOfWeek.class)),
                List.copyOf(parse(row.getPriorities(), IncidentPriority.class)),
                holidays.findAllByOrderByDayAsc().stream()
                        .map(day -> new BusinessHoursDto.Holiday(day.getDay(), day.getName()))
                        .toList());
    }

    @Transactional
    public BusinessHoursDto update(BusinessHoursDto request, UUID adminId) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone().trim());
        } catch (DateTimeException exception) {
            throw new InvalidBusinessHoursException("Unknown time zone: " + request.timeZone());
        }
        LocalTime start = LocalTime.parse(request.dayStart());
        LocalTime end = LocalTime.parse(request.dayEnd());
        if (!start.isBefore(end)) {
            throw new InvalidBusinessHoursException("The working day must start before it ends.");
        }
        if (request.workingDays().isEmpty()) {
            throw new InvalidBusinessHoursException("Choose at least one working day.");
        }
        Set<LocalDate> seen = new HashSet<>();
        for (BusinessHoursDto.Holiday holiday : request.holidays()) {
            if (!seen.add(holiday.date())) {
                throw new InvalidBusinessHoursException("The holiday on " + holiday.date() + " is listed twice.");
            }
        }

        row().update(
                request.enabled(), zone.getId(), start, end,
                join(EnumSet.copyOf(request.workingDays())),
                request.priorities().isEmpty() ? "" : join(EnumSet.copyOf(request.priorities())),
                clock.instant());
        holidays.deleteAllInBatch();
        holidays.saveAll(request.holidays().stream()
                .map(holiday -> new SlaHoliday(holiday.date(), holiday.name()))
                .toList());
        holidays.flush();

        log.info("Admin {} updated SLA business hours (enabled: {})", adminId, request.enabled());
        return get();
    }

    private static String join(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static <E extends Enum<E>> Set<E> parse(String csv, Class<E> type) {
        Set<E> values = EnumSet.noneOf(type);
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> values.add(Enum.valueOf(type, value)));
        return values;
    }
}
