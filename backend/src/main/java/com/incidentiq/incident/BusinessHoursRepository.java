package com.incidentiq.incident;

import org.springframework.data.jpa.repository.JpaRepository;

interface BusinessHoursRepository extends JpaRepository<BusinessHoursSettings, Short> {
}
