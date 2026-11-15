package com.incidentiq.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.incidentiq.user.UserAccount;

public interface IncidentWatcherRepository extends JpaRepository<IncidentWatcher, UUID> {

    boolean existsByIncidentIdAndUserId(UUID incidentId, UUID userId);

    long countByIncidentId(UUID incidentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IncidentWatcher w where w.incidentId = :incidentId and w.userId = :userId")
    int stopWatching(UUID incidentId, UUID userId);

    /** The people watching, by name. */
    @Query("""
            select u from UserAccount u
             where u.id in (select w.userId from IncidentWatcher w where w.incidentId = :incidentId)
             order by u.displayName""")
    List<UserAccount> findWatchers(UUID incidentId);
}
