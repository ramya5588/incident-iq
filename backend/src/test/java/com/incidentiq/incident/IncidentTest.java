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

        incident.updateStatus(IncidentStatus.RESOLVED);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getResolvedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void reopeningClearsResolvedAt() {
        incident.updateStatus(IncidentStatus.RESOLVED);

        incident.updateStatus(IncidentStatus.OPEN);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void movingBackToInProgressClearsResolvedAt() {
        incident.updateStatus(IncidentStatus.RESOLVED);

        incident.updateStatus(IncidentStatus.IN_PROGRESS);

        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void closingAfterResolvingKeepsResolvedAt() {
        incident.updateStatus(IncidentStatus.RESOLVED);
        Instant resolvedAt = incident.getResolvedAt();

        incident.updateStatus(IncidentStatus.CLOSED);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void assignToSetsAndClearsAssignee() {
        UserAccount agent = new UserAccount("agent@incidentiq.test", "Agent", UserRole.AGENT);

        incident.assignTo(agent);
        assertThat(incident.getAssignedTo()).isSameAs(agent);

        incident.assignTo(null);
        assertThat(incident.getAssignedTo()).isNull();
    }
}
