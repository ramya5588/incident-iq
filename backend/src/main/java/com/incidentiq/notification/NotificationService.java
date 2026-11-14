package com.incidentiq.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.incident.Incident;
import com.incidentiq.incident.IncidentStatus;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

/**
 * Creates and reads in-app notifications. The notify* methods run inside the
 * caller's transaction, so a notification exists exactly when the change
 * that caused it does. Nobody is ever notified about their own action.
 */
@Service
public class NotificationService {

    static final int LIST_LIMIT = 30;

    private final NotificationRepository repository;
    private final UserAccountRepository userRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final EmailSettings emailSettings;

    public NotificationService(
            NotificationRepository repository,
            UserAccountRepository userRepository,
            Clock clock,
            ApplicationEventPublisher events,
            EmailSettings emailSettings) {

        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.events = events;
        this.emailSettings = emailSettings;
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
            // Deactivated members don't get notifications.
            if (recipient != null && recipient.isActive()) {
                unique.putIfAbsent(recipient.getId(), recipient);
            }
        }
        if (actor != null) {
            unique.remove(actor.getId());
        }

        Instant now = now();
        for (UserAccount recipient : unique.values()) {
            repository.save(new Notification(recipient, incident, type, message, actor, now));
            if (emailSettings.enabled() && wantsEmail(recipient, type)) {
                // Sent after this transaction commits (see EmailNotifier).
                events.publishEvent(new NotificationEmail(
                        recipient.getId(), recipient.getEmail(), recipient.getDisplayName(),
                        message, incident.getId()));
            }
        }
    }

    public void notifyAssigned(Incident incident, UserAccount assignee, UserAccount actor) {
        send(List.of(assignee), incident, NotificationType.ASSIGNED,
                name(actor) + " assigned you " + label(incident), actor);
    }

    public void notifyCommented(Incident incident, UserAccount author) {
        notifyCommented(incident, author, false);
    }

    /** {@code resumed}: the comment took the incident off hold. */
    public void notifyCommented(Incident incident, UserAccount author, boolean resumed) {
        String message = resumed
                ? name(author) + " replied on " + label(incident) + ", so it's back in progress"
                : name(author) + " commented on " + label(incident);
        send(Arrays.asList(incident.getCreatedBy(), incident.getAssignedTo()),
                incident, NotificationType.COMMENTED, message, author);
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

    static boolean wantsEmail(UserAccount user, NotificationType type) {
        return switch (type) {
            case ASSIGNED -> user.isEmailOnAssigned();
            case COMMENTED -> user.isEmailOnCommented();
            case STATUS_CHANGED -> user.isEmailOnStatusChanged();
            case SLA_BREACHED -> user.isEmailOnSlaBreached();
        };
    }

    private static String statusLabel(IncidentStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In progress";
            case ON_HOLD -> "On hold";
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

    // --- email preferences -------------------------------------------------

    @Transactional(readOnly = true)
    public EmailPreferences.Response emailPreferences(UUID userId) {
        return EmailPreferences.Response.of(findUser(userId), emailSettings.enabled());
    }

    @Transactional
    public EmailPreferences.Response updateEmailPreferences(
            UUID userId, EmailPreferences preferences) {

        UserAccount user = findUser(userId);
        user.setEmailPreferences(
                preferences.assigned(), preferences.commented(),
                preferences.statusChanged(), preferences.slaBreached());
        return EmailPreferences.Response.of(user, emailSettings.enabled());
    }

    private UserAccount findUser(UUID userId) {
        // The signed-in user; the session filter has already checked they exist.
        return userRepository.findById(userId).orElseThrow();
    }
}
