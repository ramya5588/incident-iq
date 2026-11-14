package com.incidentiq.incident;

import java.util.Set;

/**
 * The calendar each priority's deadlines use right now: business hours for
 * the chosen priorities when they're on, otherwise around the clock.
 */
record SlaCalendars(SlaCalendar businessHours, Set<IncidentPriority> priorities) {

    static final SlaCalendars ALWAYS = new SlaCalendars(null, Set.of());

    SlaCalendar forPriority(IncidentPriority priority) {
        return businessHours != null && priorities.contains(priority)
                ? businessHours
                : SlaCalendar.ALWAYS;
    }
}
