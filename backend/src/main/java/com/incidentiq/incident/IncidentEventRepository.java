package com.incidentiq.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {

    /** Newest first; id breaks ties between events in the same instant. */
    @EntityGraph(attributePaths = "actor")
    List<IncidentEvent> findAllByIncidentIdOrderByIdDesc(UUID incidentId);

    /** The same without internal notes, for reporters. */
    @EntityGraph(attributePaths = "actor")
    List<IncidentEvent> findAllByIncidentIdAndInternalFalseOrderByIdDesc(UUID incidentId);
}
