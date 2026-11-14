package com.incidentiq.incident;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The single row of business-hours settings (id 1). */
@Entity
@Table(name = "sla_business_hours")
public class BusinessHoursSettings {

    static final short ID = 1;

    @Id
    private Short id = ID;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "day_start", nullable = false)
    private LocalTime dayStart;

    @Column(name = "day_end", nullable = false)
    private LocalTime dayEnd;

    @Column(name = "working_days", nullable = false, length = 70)
    private String workingDays;

    @Column(nullable = false, length = 40)
    private String priorities;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected BusinessHoursSettings() {
    }

    void update(boolean enabled, String timeZone, LocalTime dayStart, LocalTime dayEnd,
            String workingDays, String priorities, Instant now) {
        this.enabled = enabled;
        this.timeZone = timeZone;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.workingDays = workingDays;
        this.priorities = priorities;
        this.updatedAt = now;
    }

    boolean isEnabled() {
        return enabled;
    }

    String getTimeZone() {
        return timeZone;
    }

    LocalTime getDayStart() {
        return dayStart;
    }

    LocalTime getDayEnd() {
        return dayEnd;
    }

    String getWorkingDays() {
        return workingDays;
    }

    String getPriorities() {
        return priorities;
    }
}
