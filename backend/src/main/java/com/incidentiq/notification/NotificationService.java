package com.incidentiq.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.incident.Incident;
import com.incidentiq.incident.IncidentStatus;
import com.incidentiq.user.UserAccount;

/**
 * Creates and reads in-app notifications. The notify* methods run inside the
 * caller's transaction, so a notification exists exactly when the change
 * that caused it does. Nobody is ever notified about their own action.
 */
@Service
public class NotificationService {

    static final int LIST_LIMIT = 30;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** "INC-007 “VPN drops”". */
    static String label(Incident incident) {
        return "INC-%03d “%s”".formatted(incident.getIncidentNumber(), incident.getTitle());
    }

    private static String name(UserAccount user) {
        return user == null ? "Someone" : user.getDisplayName();
    }

    /** Sends one notification to each distinct recipient except the actor. */
    private void send(
            List<UserAccount> recipients,
            Incident incident,
            NotificationType type,
            String message,
            UserAccount actor) {

        Map<UUID, UserAccount> unique = new LinkedHashMap<>();
        for (UserAccount recipient : recipients) {
            if (recipient != null) {
                unique.putIfAbsent(recipient.getId(), recipient);
            }
        }
        if (actor != null) {
            unique.remove(actor.getId());
        }

        Instant now = now();
        for (UserAccount recipient : unique.values()) {
            repository.save(new Notification(recipient, incident, type, message, actor, now));
        }
    }

    public void notifyAssigned(Incident incident, UserAccount assignee, UserAccount actor) {
        send(List.of(assignee), incident, NotificationType.ASSIGNED,
                name(actor) + " assigned you " + label(incident), actor);
    }

    public void notifyCommented(Incident incident, UserAccount author) {
        send(Arrays.asList(incident.getCreatedBy(), incident.getAssignedTo()),
                incident, NotificationType.COMMENTED,
                name(author) + " commented on " + label(incident), author);
    }

    public void notifyStatusChanged(
            Incident incident, IncidentStatus status, UserAccount actor) {

        send(List.of(incident.getCreatedBy()), incident, NotificationType.STATUS_CHANGED,
                name(actor) + " changed " + label(incident) + " to " + statusLabel(status), actor);
    }

    /** {@code recipients}: the assignee, or every admin when unassigned. */
    public void notifySlaBreached(
            Incident incident, boolean response, List<UserAccount> recipients) {

        String deadline = response ? "response" : "resolution";
        send(recipients, incident, NotificationType.SLA_BREACHED,
                label(incident) + " missed its " + deadline + " deadline", null);
    }

    private static String statusLabel(IncidentStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In progress";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }

    // --- reading ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationResponse> recent(UUID recipientId) {
        return repository
                .findByRecipientIdOrderByIdDesc(recipientId, PageRequest.of(0, LIST_LIMIT))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    /**
     * Marks one of the recipient's notifications read. Someone else's
     * notification looks exactly like a missing one (404).
     */
    @Transactional
    public void markRead(Long notificationId, UUID recipientId) {
        if (repository.markRead(notificationId, recipientId, now()) == 0
                && !repository.existsByIdAndRecipientId(notificationId, recipientId)) {
            throw new NotificationNotFoundException(notificationId);
        }
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return repository.markAllRead(recipientId, now());
    }
}
