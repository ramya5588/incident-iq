package com.incidentiq.incident;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Page<Incident> findAllByStatus(
            IncidentStatus status,
            Pageable pageable);

    Page<Incident> findAllByPriority(
            IncidentPriority priority,
            Pageable pageable);
}