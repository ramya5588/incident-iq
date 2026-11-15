package com.incidentiq.incident;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.user.UserAccount;

/** Following incidents. Anyone signed in can watch any incident they can see. */
@Service
public class WatcherService {

    private final IncidentWatcherRepository watchers;
    private final IncidentRepository incidents;
    private final Clock clock;

    public WatcherService(IncidentWatcherRepository watchers, IncidentRepository incidents, Clock clock) {
        this.watchers = watchers;
        this.incidents = incidents;
        this.clock = clock;
    }

    /**
     * Whether you watch it, how many do, and (for agents and admins) who.
     * Reporters get only the count, so watching stays private between them.
     */
    @Transactional(readOnly = true)
    public WatchersResponse get(UUID incidentId, UUID userId, boolean showNames) {
        requireIncident(incidentId);
        List<UserAccount> people = watchers.findWatchers(incidentId);
        return new WatchersResponse(
                people.stream().anyMatch(user -> user.getId().equals(userId)),
                people.size(),
                showNames
                        ? people.stream()
                                .map(user -> new WatchersResponse.Watcher(user.getId(), user.getDisplayName()))
                                .toList()
                        : null);
    }

    @Transactional
    public WatchersResponse watch(UUID incidentId, UUID userId, boolean showNames) {
        requireIncident(incidentId);
        startWatching(incidentId, List.of(userId));
        return get(incidentId, userId, showNames);
    }

    @Transactional
    public WatchersResponse stopWatching(UUID incidentId, UUID userId, boolean showNames) {
        requireIncident(incidentId);
        watchers.stopWatching(incidentId, userId);
        return get(incidentId, userId, showNames);
    }

    /** Adds these people as watchers; ones already watching are left as they are. */
    @Transactional
    public void startWatching(UUID incidentId, Collection<UUID> userIds) {
        for (UUID userId : userIds) {
            if (!watchers.existsByIncidentIdAndUserId(incidentId, userId)) {
                watchers.saveAndFlush(new IncidentWatcher(
                        incidentId, userId, clock.instant().truncatedTo(ChronoUnit.MICROS)));
            }
        }
    }

    private void requireIncident(UUID incidentId) {
        if (!incidents.existsById(incidentId)) {
            throw new IncidentNotFoundException(incidentId);
        }
    }
}
