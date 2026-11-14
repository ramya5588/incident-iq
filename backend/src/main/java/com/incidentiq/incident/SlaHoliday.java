package com.incidentiq.incident;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A day that doesn't count toward business-hours deadlines. */
@Entity
@Table(name = "sla_holidays")
public class SlaHoliday {

    @Id
    private LocalDate day;

    @Column(nullable = false, length = 100)
    private String name;

    protected SlaHoliday() {
    }

    SlaHoliday(LocalDate day, String name) {
        this.day = day;
        this.name = name;
    }

    LocalDate getDay() {
        return day;
    }

    String getName() {
        return name;
    }
}
