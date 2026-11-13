package com.incidentiq.incident;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository
        extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    long countByStatus(IncidentStatus status);

    long countByResolvedAtGreaterThanEqual(Instant since);

    long countByAiTriagedAtIsNotNull();

    @Query("""
            select i.assignedTo.id as userId, count(i) as activeCount
            from Incident i
            where i.assignedTo is not null
              and i.status in :statuses
            group by i.assignedTo.id
            """)
    List<AssigneeWorkload> countActiveByAssignee(
            @Param("statuses") Collection<IncidentStatus> statuses);

    /**
     * Open or in-progress incidents with a deadline that has passed but
     * whose breach hasn't been announced yet.
     */
    @EntityGraph(attributePaths = "assignedTo")
    @Query("""
            select i from Incident i
            where i.status in :statuses
              and ((i.firstRespondedAt is null
                    and i.respondBy <= :now
                    and i.responseBreachNotifiedAt is null)
                or (i.resolveBy <= :now
                    and i.resolutionBreachNotifiedAt is null))
            """)
    List<Incident> findUnannouncedBreaches(
            @Param("statuses") Collection<IncidentStatus> statuses,
            @Param("now") Instant now);

    interface AssigneeWorkload {
        UUID getUserId();

        long getActiveCount();
    }
}