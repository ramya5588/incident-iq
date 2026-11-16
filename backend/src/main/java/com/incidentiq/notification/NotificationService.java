package com.incidentiq.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.incident.Incident;
import com.incidentiq.incident.IncidentWatcherRepository;
import com.incidentiq.incident.IncidentStatus;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

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
    private final IncidentWatcherRepository watcherRepository;

    public NotificationService(
            NotificationRepository repository,
            UserAccountRepository userRepository,
            Clock clock,
            ApplicationEventPublisher events,
            EmailSettings emailSettings,
            IncidentWatcherRepository watcherRepository) {

        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.events = events;
        this.emailSettings = emailSettings;
        this.watcherRepository = watcherRepository;
    }

    /** {@code people} plus the incident's watchers (duplicates are fine: send() merges them). */
    private List<UserAccount> withWatchers(Incident incident, List<UserAccount> people) {
        List<UserAccount> all = new ArrayList<>(people);
        all.addAll(watcherRepository.findWatchers(incident.getId()));
        return all;
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
        notifyCommented(incident, author, resumed, List.of());
    }

    /** {@code mentioned} were already told by a mention, so aren't told twice. */
    public void notifyCommented(
            Incident incident, UserAccount author, boolean resumed,
            Collection<UserAccount> mentioned) {

        String message = resumed
                ? name(author) + " replied on " + label(incident) + ", so it's back in progress"
                : name(author) + " commented on " + label(incident);
        List<UserAccount> involved = withWatchers(
                incident, Arrays.asList(incident.getCreatedBy(), incident.getAssignedTo()));
        send(without(involved, mentioned), incident, NotificationType.COMMENTED, message, author);
    }

    /** People mentioned in a comment or internal note (already checked as allowed). */
    public void notifyMentioned(
            Incident incident, UserAccount author, List<UserAccount> mentioned, boolean internal) {

        String where = internal ? " in an internal note on " : " on ";
        send(mentioned, incident, NotificationType.MENTIONED,
                name(author) + " mentioned you" + where + label(incident), author);
    }

    private static List<UserAccount> without(
            List<UserAccount> recipients, Collection<UserAccount> exclude) {

        Set<UUID> excluded = new HashSet<>();
        exclude.forEach(user -> excluded.add(user.getId()));
        List<UserAccount> remaining = new ArrayList<>();
        for (UserAccount recipient : recipients) {
            if (recipient != null && !excluded.contains(recipient.getId())) {
                remaining.add(recipient);
            }
        }
        return remaining;
    }

    /**
     * An internal note goes to the reporter and assignee, but only those who
     * can see internal notes (never someone with the USER role).
     */
    public void notifyInternalNote(
            Incident incident, UserAccount author, Collection<UserAccount> mentioned) {

        List<UserAccount> team = new ArrayList<>();
        for (UserAccount user : withWatchers(
                incident, Arrays.asList(incident.getCreatedBy(), incident.getAssignedTo()))) {
            if (user != null && user.getRole() != UserRole.USER) {
                team.add(user);
            }
        }
        send(without(team, mentioned), incident, NotificationType.COMMENTED,
                name(author) + " added an internal note on " + label(incident), author);
    }

    /** The duplicate's reporter and watchers learn where to follow along. */
    public void notifyMerged(Incident duplicate, Incident main, UserAccount actor) {
        send(withWatchers(duplicate, List.of(duplicate.getCreatedBy())), duplicate,
                NotificationType.STATUS_CHANGED,
                name(actor) + " closed " + label(duplicate) + " as a duplicate of " + label(main)
                        + ". Updates continue there.", actor);
    }

    public void notifyStatusChanged(
            Incident incident, IncidentStatus status, UserAccount actor) {

        send(withWatchers(incident, List.of(incident.getCreatedBy())), incident, NotificationType.STATUS_CHANGED,
                name(actor) + " changed " + label(incident) + " to " + statusLabel(status), actor);
    }

    /** {@code recipients}: the assignee, or every admin when unassigned. */
    public void notifySlaBreached(
            Incident incident, boolean response, List<UserAccount> recipients) {

        String deadline = response ? "response" : "resolution";
        send(withWatchers(incident, recipients), incident, NotificationType.SLA_BREACHED,
                label(incident) + " missed its " + deadline + " deadline", null);
    }

    static boolean wantsEmail(UserAccount user, NotificationType type) {
        return switch (type) {
            case ASSIGNED -> user.isEmailOnAssigned();
            case COMMENTED -> user.isEmailOnCommented();
            case MENTIONED -> user.isEmailOnMentioned();
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
        if (preferences.mentioned() != null) {
            user.setEmailOnMentioned(preferences.mentioned());
        }
        return EmailPreferences.Response.of(user, emailSettings.enabled());
    }

    private UserAccount findUser(UUID userId) {
        // The signed-in user; the session filter has already checked they exist.
        return userRepository.findById(userId).orElseThrow();
    }
}
