package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserRole;

class IncidentTest {

    private UserAccount creator;
    private Incident incident;

    @BeforeEach
    void setUp() {
        creator = new UserAccount("creator@incidentiq.test", "Creator", UserRole.USER);
        incident = new Incident(
                "Checkout is down",
                "Payments fail with a 500",
                IncidentPriority.HIGH,
                "Payments",
                creator);
    }

    @Test
    void newIncidentIsOpenAndUnassigned() {
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getAssignedTo()).isNull();
        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void resolvingSetsResolvedAt() {
        Instant before = Instant.now();

        incident.updateStatus(IncidentStatus.RESOLVED, Instant.now());

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getResolvedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void reopeningClearsResolvedAt() {
        incident.updateStatus(IncidentStatus.RESOLVED, Instant.now());

        incident.updateStatus(IncidentStatus.OPEN, Instant.now());

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void movingBackToInProgressClearsResolvedAt() {
        incident.updateStatus(IncidentStatus.RESOLVED, Instant.now());

        incident.updateStatus(IncidentStatus.IN_PROGRESS, Instant.now());

        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void closingAfterResolvingKeepsResolvedAt() {
        incident.updateStatus(IncidentStatus.RESOLVED, Instant.now());
        Instant resolvedAt = incident.getResolvedAt();

        incident.updateStatus(IncidentStatus.CLOSED, Instant.now());

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void assignToSetsAndClearsAssignee() {
        UserAccount agent = new UserAccount("agent@incidentiq.test", "Agent", UserRole.AGENT);

        incident.assignTo(agent, Instant.now());
        assertThat(incident.getAssignedTo()).isSameAs(agent);

        incident.assignTo(null, Instant.now());
        assertThat(incident.getAssignedTo()).isNull();
    }

    @Test
    void firstResponseIsWhenFirstAssignedOrMovedOutOfOpen() {
        Instant reported = Instant.parse("2026-01-05T09:00:00Z");
        incident.startSla(reported, new SlaProperties.Target(
                java.time.Duration.ofHours(1), java.time.Duration.ofHours(8)));
        assertThat(incident.getRespondBy()).isEqualTo(reported.plusSeconds(3600));
        assertThat(incident.getResolveBy()).isEqualTo(reported.plusSeconds(8 * 3600));

        incident.assignTo(null, reported.plusSeconds(60));
        assertThat(incident.getFirstRespondedAt()).isNull();

        incident.updateStatus(IncidentStatus.IN_PROGRESS, reported.plusSeconds(600));
        assertThat(incident.getFirstRespondedAt()).isEqualTo(reported.plusSeconds(600));

        // Later changes never move the first response.
        incident.updateStatus(IncidentStatus.OPEN, reported.plusSeconds(900));
        incident.assignTo(
                new UserAccount("a@incidentiq.test", "A", UserRole.AGENT),
                reported.plusSeconds(1200));
        assertThat(incident.getFirstRespondedAt()).isEqualTo(reported.plusSeconds(600));
    }

    @Test
    void changingPriorityRecalculatesDeadlinesFromReportedTime() {
        Instant reported = Instant.parse("2026-01-05T09:00:00Z");
        incident.startSla(reported, new SlaProperties.Target(
                java.time.Duration.ofHours(1), java.time.Duration.ofHours(8)));

        incident.changePriority(IncidentPriority.CRITICAL, new SlaProperties.Target(
                java.time.Duration.ofMinutes(15), java.time.Duration.ofHours(4)));

        assertThat(incident.getPriority()).isEqualTo(IncidentPriority.CRITICAL);
        assertThat(incident.getRespondBy()).isEqualTo(reported.plusSeconds(15 * 60));
        assertThat(incident.getResolveBy()).isEqualTo(reported.plusSeconds(4 * 3600));
    }
}
