package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.MutableClock;
import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.notification.SlaBreachNotifier;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

/**
 * Putting incidents on hold: the resolution clock pauses, and the time on
 * hold moves the deadline later. Defaults: HIGH resolves in 8h, CRITICAL 4h.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@Transactional
class OnHoldApiTest {

    private static final Instant T0 = Instant.parse("2026-01-05T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private SlaBreachNotifier breachNotifier;

    private UserAccount agent;
    private UserAccount reporter;

    @BeforeEach
    void setUp() {
        clock.setInstant(T0);
        agent = userRepository.saveAndFlush(new UserAccount(
                "hold.agent@incidentiq.test", "Hold Agent", UserRole.AGENT));
        reporter = userRepository.saveAndFlush(new UserAccount(
                "hold.reporter@incidentiq.test", "Riley Reporter", UserRole.USER));
        TestAuth.signInAs(agent);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private String createIncident(UserAccount as, String priority) throws Exception {
        TestAuth.signInAs(as);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Laptop won't boot", "description": "Black screen",
                                 "priority": "%s", "category": "Hardware"}
                                """.formatted(priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        TestAuth.signInAs(agent);
        String id = JsonPath.read(body, "$.id");
        mockMvc.perform(patch("/api/incidents/{id}/assignee", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + agent.getId() + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions setStatus(String id, String json) throws Exception {
        return mockMvc.perform(patch("/api/incidents/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions hold(String id, String reason, String note) throws Exception {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return setStatus(id, """
                {"status": "ON_HOLD", "holdReason": "%s", "holdNote": %s}
                """.formatted(reason, noteJson));
    }

    private ResultActions getIncident(String id) throws Exception {
        return mockMvc.perform(get("/api/incidents/{id}", id));
    }

    private ResultActions comment(String id, UserAccount as, String text) throws Exception {
        TestAuth.signInAs(as);
        ResultActions result = mockMvc.perform(post("/api/incidents/{id}/comments", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \"" + text + "\"}"));
        TestAuth.signInAs(agent);
        return result;
    }

    @Test
    void holdPausesTheResolutionClockAndMovesTheDeadline() throws Exception {
        String id = createIncident(reporter, "HIGH");
        clock.advance(Duration.ofHours(2));

        hold(id, "WAITING_ON_THIRD_PARTY", "  Vendor ticket 4471 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.holdReason").value("WAITING_ON_THIRD_PARTY"))
                .andExpect(jsonPath("$.holdNote").value("Vendor ticket 4471"))
                .andExpect(jsonPath("$.onHoldSince").value("2026-01-05T11:00:00Z"))
                .andExpect(jsonPath("$.sla.resolution.state").value("PAUSED"))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-05T17:00:00Z"));

        // Well past the original 17:00 deadline, but it's paused: not overdue.
        clock.advance(Duration.ofHours(10));
        getIncident(id)
                .andExpect(jsonPath("$.sla.resolution.state").value("PAUSED"))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-06T03:00:00Z"));
        mockMvc.perform(get("/api/incidents").param("overdue", "true"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/incidents/stats"))
                .andExpect(jsonPath("$.onHold").value(1))
                .andExpect(jsonPath("$.slaBreached").value(0));
        assertThat(breachNotifier.announceBreaches()).isZero();

        // Back to work: 6h of the 8h are still left.
        setStatus(id, "{\"status\": \"IN_PROGRESS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdReason").doesNotExist())
                .andExpect(jsonPath("$.onHoldSince").doesNotExist())
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-06T03:00:00Z"))
                .andExpect(jsonPath("$.sla.resolution.state").value("ON_TRACK"));

        mockMvc.perform(get("/api/incidents/{id}/events", id))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].fromValue").value("ON_HOLD"))
                .andExpect(jsonPath("$[0].toValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].toValue").value("ON_HOLD"))
                .andExpect(jsonPath("$[1].message").value("Waiting on third party: Vendor ticket 4471"));

        // Recalculating for a new priority keeps the paused time: 09:00 + 4h + 10h.
        mockMvc.perform(patch("/api/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"CRITICAL\"}"))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-05T23:00:00Z"));
    }

    @Test
    void everyHoldAddsUpAndResolvingFromHoldCountsAgainstTheMovedDeadline() throws Exception {
        String id = createIncident(reporter, "HIGH");

        hold(id, "SCHEDULED", null).andExpect(status().isOk());
        clock.advance(Duration.ofHours(3));
        setStatus(id, "{\"status\": \"IN_PROGRESS\"}").andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));
        hold(id, "OTHER", "Waiting for parts").andExpect(status().isOk());
        clock.advance(Duration.ofHours(6));

        // 9h on hold in total, 1h of work: resolving now is on time.
        setStatus(id, "{\"status\": \"RESOLVED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").value("2026-01-05T19:00:00Z"))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-06T02:00:00Z"))
                .andExpect(jsonPath("$.sla.resolution.state").value("MET"));
    }

    @Test
    void aDeadlineMissedBeforeTheHoldStaysMissed() throws Exception {
        String id = createIncident(reporter, "HIGH");
        clock.advance(Duration.ofHours(9));

        hold(id, "WAITING_ON_REPORTER", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.resolution.state").value("BREACHED"))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-05T17:00:00Z"));
    }

    @Test
    void reporterReplyTakesItOffHold() throws Exception {
        String id = createIncident(reporter, "HIGH");
        hold(id, "WAITING_ON_REPORTER", "Asked for the error message").andExpect(status().isOk());
        clock.advance(Duration.ofHours(2));

        // The agent's own comment doesn't count as the reply.
        comment(id, agent, "Any news?").andExpect(status().isCreated());
        getIncident(id).andExpect(jsonPath("$.status").value("ON_HOLD"));

        comment(id, reporter, "It says 0xc000000f").andExpect(status().isCreated());

        getIncident(id)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.holdReason").doesNotExist())
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-05T19:00:00Z"));
        mockMvc.perform(get("/api/incidents/{id}/events", id))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].message").value("Resumed when the reporter replied"))
                .andExpect(jsonPath("$[0].actorName").value("Riley Reporter"))
                .andExpect(jsonPath("$[1].type").value("COMMENT"));
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[0].message").value(Matchers.matchesPattern(
                        "Riley Reporter replied on INC-\\d{3,} “Laptop won't boot”, so it's back in progress")));
    }

    @Test
    void reporterCommentsDontResumeOtherHolds() throws Exception {
        String id = createIncident(reporter, "HIGH");
        hold(id, "WAITING_ON_THIRD_PARTY", null).andExpect(status().isOk());

        comment(id, reporter, "Any update?").andExpect(status().isCreated());

        getIncident(id).andExpect(jsonPath("$.status").value("ON_HOLD"));
    }

    @Test
    void holdNeedsAReasonAndCanBeUpdatedWithoutRestartingIt() throws Exception {
        String id = createIncident(reporter, "HIGH");

        setStatus(id, "{\"status\": \"ON_HOLD\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose why the incident is on hold."));
        hold(id, "WAITING_ON_REPORTER", "x".repeat(201)).andExpect(status().isBadRequest());
        getIncident(id).andExpect(jsonPath("$.status").value("OPEN"));

        hold(id, "WAITING_ON_REPORTER", null).andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));
        hold(id, "WAITING_ON_THIRD_PARTY", "Escalated to vendor")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdReason").value("WAITING_ON_THIRD_PARTY"))
                .andExpect(jsonPath("$.onHoldSince").value("2026-01-05T09:00:00Z"));

        // On-hold work still counts toward the assignee's workload.
        mockMvc.perform(get("/api/incidents/workload"))
                .andExpect(jsonPath("$[?(@.userId == '%s')].activeCount".formatted(agent.getId()))
                        .value(1));
    }

    @Test
    void reportersCantPutIncidentsOnHold() throws Exception {
        String id = createIncident(reporter, "HIGH");
        TestAuth.signInAs(reporter);

        hold(id, "OTHER", null).andExpect(status().isForbidden());
    }
}
