package com.incidentiq.incident;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

    interface AssigneeWorkload {
        UUID getUserId();

        long getActiveCount();
    }
}