package com.incidentiq.incident;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Business-hours settings as the API shows and takes them. Times are
 * "HH:mm" in {@code timeZone}; holidays are dates in that zone.
 *
 * @param priorities priorities whose deadlines count business hours only
 */
public record BusinessHoursDto(
        boolean enabled,
        @NotBlank(message = "Time zone is required") @Size(max = 64) String timeZone,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Use HH:mm for the start time")
        String dayStart,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Use HH:mm for the end time")
        String dayEnd,
        @NotNull List<DayOfWeek> workingDays,
        @NotNull List<IncidentPriority> priorities,
        @NotNull @Size(max = 366, message = "At most 366 holidays") List<@Valid Holiday> holidays) {

    public record Holiday(
            @NotNull(message = "Each holiday needs a date") LocalDate date,
            @NotBlank(message = "Each holiday needs a name")
            @Size(max = 100, message = "Holiday names must not exceed 100 characters")
            String name) {

        public Holiday {
            name = name == null ? null : name.trim();
        }
    }
}
