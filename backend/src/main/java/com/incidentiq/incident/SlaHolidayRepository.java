package com.incidentiq.incident;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SlaHolidayRepository extends JpaRepository<SlaHoliday, LocalDate> {

    List<SlaHoliday> findAllByOrderByDayAsc();
}
