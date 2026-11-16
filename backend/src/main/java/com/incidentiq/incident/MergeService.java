package com.incidentiq.incident;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.notification.NotificationService;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

/**
 * Marking an incident as a duplicate of another (the main one). The
 * duplicate is closed and points at the main incident; its reporter and
 * watchers start watching the main one, so they keep getting updates.
 * Undoing it reopens the duplicate.
 */
@Service
public class MergeService {

    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final UserAccountRepository users;
    private final IncidentWatcherRepository watcherRepository;
    private final WatcherService watchers;
    private final NotificationService notifications;
    private final BusinessHoursService businessHours;
    private final IncidentService incidentService;
    private final Clock clock;

    public MergeService(
            IncidentRepository incidents,
            IncidentEventRepository events,
            UserAccountRepository users,
            IncidentWatcherRepository watcherRepository,
            WatcherService watchers,
            NotificationService notifications,
            BusinessHoursService businessHours,
            IncidentService incidentService,
            Clock clock) {

        this.incidents = incidents;
        this.events = events;
        this.users = users;
        this.watcherRepository = watcherRepository;
        this.watchers = watchers;
        this.notifications = notifications;
        this.businessHours = businessHours;
        this.incidentService = incidentService;
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static String number(Incident incident) {
        return "INC-%03d".formatted(incident.getIncidentNumber());
    }

    @Transactional
    public IncidentResponse markDuplicate(UUID duplicateId, UUID mainId, UUID actorId) {
        Incident duplicate = find(duplicateId);
        Incident main = find(mainId);
        UserAccount actor = users.findById(actorId).orElseThrow();

        if (duplicate.getId().equals(main.getId())) {
            throw new InvalidMergeException("An incident can't be a duplicate of itself.");
        }
        if (duplicate.getDuplicateOf() != null) {
            throw new InvalidMergeException(number(duplicate) + " is already a duplicate of "
                    + number(duplicate.getDuplicateOf()) + ". Undo that first.");
        }
        if (main.getDuplicateOf() != null) {
            throw new InvalidMergeException(number(main) + " is itself a duplicate of "
                    + number(main.getDuplicateOf()) + ". Choose that one instead.");
        }
        if (incidents.existsByDuplicateOfId(duplicate.getId())) {
            throw new InvalidMergeException(number(duplicate)
                    + " has duplicates merged into it, so it can't become a duplicate itself.");
        }

        IncidentStatus previous = duplicate.getStatus();
        duplicate.markDuplicateOf(main);
        if (previous != IncidentStatus.CLOSED) {
            duplicate.updateStatus(IncidentStatus.CLOSED, now(),
                    businessHours.calendars().forPriority(duplicate.getPriority()));
            events.save(IncidentEvent.statusChanged(duplicate, previous, IncidentStatus.CLOSED,
                    "Duplicate of " + number(main), actor));
        }
        events.save(IncidentEvent.merge(duplicate, IncidentEventType.MERGED_INTO, number(main), actor));
        events.save(IncidentEvent.merge(main, IncidentEventType.DUPLICATE_MERGED, number(duplicate), actor));
        incidents.saveAndFlush(duplicate);

        // Everyone following the duplicate now follows the main incident.
        List<UUID> followers = new ArrayList<>();
        followers.add(duplicate.getCreatedBy().getId());
        watcherRepository.findWatchers(duplicate.getId()).forEach(user -> followers.add(user.getId()));
        watchers.startWatching(main.getId(), followers.stream()
                .filter(id -> !id.equals(main.getCreatedBy().getId()))
                .distinct()
                .toList());
        notifications.notifyMerged(duplicate, main, actor);

        return incidentService.getIncident(duplicate.getId());
    }

    @Transactional
    public IncidentResponse undo(UUID duplicateId, UUID actorId) {
        Incident duplicate = find(duplicateId);
        UserAccount actor = users.findById(actorId).orElseThrow();
        Incident main = duplicate.getDuplicateOf();
        if (main == null) {
            throw new InvalidMergeException(number(duplicate) + " isn't marked as a duplicate.");
        }

        duplicate.markDuplicateOf(null);
        IncidentStatus previous = duplicate.getStatus();
        duplicate.updateStatus(IncidentStatus.OPEN, now(),
                businessHours.calendars().forPriority(duplicate.getPriority()));
        events.save(IncidentEvent.merge(duplicate, IncidentEventType.UNMERGED, number(main), actor));
        events.save(IncidentEvent.merge(main, IncidentEventType.DUPLICATE_UNMERGED, number(duplicate), actor));
        if (previous != IncidentStatus.OPEN) {
            events.save(IncidentEvent.statusChanged(duplicate, previous, IncidentStatus.OPEN, actor));
        }
        incidents.saveAndFlush(duplicate);
        notifications.notifyStatusChanged(duplicate, IncidentStatus.OPEN, actor);

        return incidentService.getIncident(duplicate.getId());
    }

    /** The incidents merged into this one, by number. */
    @Transactional(readOnly = true)
    public List<DuplicateResponse> duplicates(UUID mainId) {
        find(mainId);
        return incidents.findByDuplicateOfIdOrderByIncidentNumber(mainId).stream()
                .map(incident -> new DuplicateResponse(
                        incident.getId(), incident.getIncidentNumber(), incident.getTitle()))
                .toList();
    }

    private Incident find(UUID id) {
        return incidents.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
    }

    public record DuplicateResponse(UUID id, Long incidentNumber, String title) {
    }
}
