package com.incidentiq.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.incidentiq.incident.Incident;
import com.incidentiq.incident.IncidentRepository;
import com.incidentiq.incident.IncidentStatus;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/**
 * Announces missed SLA deadlines. Deadlines pass on their own (no user
 * action happens), so this checks periodically. Each deadline is announced
 * once, to the assignee, or to every admin if nobody is assigned.
 */
@Component
public class SlaBreachNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachNotifier.class);

    private final IncidentRepository incidentRepository;
    private final UserAccountRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SlaBreachNotifier(
            IncidentRepository incidentRepository,
            UserAccountRepository userRepository,
            NotificationService notifications,
            Clock clock,
            TransactionTemplate transaction) {

        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
        this.transaction = transaction;
    }

    @Scheduled(
            fixedDelayString = "${incidentiq.sla.breach-check-interval:60s}",
            initialDelayString = "${incidentiq.sla.breach-check-initial-delay:30s}")
    public void scheduledCheck() {
        try {
            int announced = announceBreaches();
            if (announced > 0) {
                log.info("Announced {} missed SLA deadline(s)", announced);
            }
        } catch (RuntimeException exception) {
            // Never let one failure stop future checks.
            log.error("SLA breach check failed", exception);
        }
    }

    /**
     * Runs in one transaction (joining the caller's, if any): notifications
     * and the "announced" markers are saved together or not at all.
     *
     * @return how many deadlines were announced
     */
    public int announceBreaches() {
        Integer announced = transaction.execute(status -> announceInTransaction());
        return announced == null ? 0 : announced;
    }

    private int announceInTransaction() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Incident> incidents = incidentRepository.findUnannouncedBreaches(
                List.of(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS), now);
        if (incidents.isEmpty()) {
            return 0;
        }

        List<UserAccount> admins = null;
        int announced = 0;

        for (Incident incident : incidents) {
            List<UserAccount> recipients;
            if (incident.getAssignedTo() != null) {
                recipients = List.of(incident.getAssignedTo());
            } else {
                if (admins == null) {
                    admins = userRepository.findByRole(UserRole.ADMIN);
                }
                recipients = admins;
            }

            if (incident.getFirstRespondedAt() == null
                    && !incident.getRespondBy().isAfter(now)
                    && incident.getResponseBreachNotifiedAt() == null) {
                notifications.notifySlaBreached(incident, true, recipients);
                incident.markResponseBreachNotified(now);
                announced++;
            }
            if (!incident.getResolveBy().isAfter(now)
                    && incident.getResolutionBreachNotifiedAt() == null) {
                notifications.notifySlaBreached(incident, false, recipients);
                incident.markResolutionBreachNotified(now);
                announced++;
            }
        }
        return announced;
    }
}
