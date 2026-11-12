package com.incidentiq.incident;

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
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

/**
 * SLA deadlines and states through the API, with a clock the test controls.
 * Default targets (application.properties): HIGH respond 1h / resolve 8h,
 * CRITICAL 15m / 4h, LOW 8h / 3d.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@Transactional
class SlaApiTest {

    private static final Instant T0 = Instant.parse("2026-01-05T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    private String agentId;

    @BeforeEach
    void setUp() {
        clock.setInstant(T0);
        UserAccount agent = userRepository.saveAndFlush(new UserAccount(
                "sla.agent@incidentiq.test", "Sla Agent", UserRole.AGENT));
        agentId = agent.getId().toString();
        TestAuth.signInAs(agent);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private String createIncident(String priority) throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "SLA test", "description": "Clock test",
                                 "priority": "%s", "category": "Testing"}
                                """.formatted(priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions getIncident(String id) throws Exception {
        return mockMvc.perform(get("/api/incidents/{id}", id));
    }

    private void patchJson(String path, String id, String json) throws Exception {
        mockMvc.perform(patch(path, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private ResultActions overdueList() throws Exception {
        return mockMvc.perform(get("/api/incidents").param("overdue", "true"));
    }

    private ResultActions stats() throws Exception {
        return mockMvc.perform(get("/api/incidents/stats"));
    }

    @Test
    void newIncidentGetsDeadlinesFromItsPriority() throws Exception {
        String id = createIncident("HIGH");

        getIncident(id)
                .andExpect(jsonPath("$.createdAt").value("2026-01-05T09:00:00Z"))
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-05T10:00:00Z"))
                .andExpect(jsonPath("$.sla.response.state").value("ON_TRACK"))
                .andExpect(jsonPath("$.sla.response.completedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-05T17:00:00Z"))
                .andExpect(jsonPath("$.sla.resolution.state").value("ON_TRACK"));
    }

    @Test
    void responseGoesAtRiskThenBreachedAndCountsAsOverdue() throws Exception {
        String id = createIncident("HIGH");

        clock.advance(Duration.ofMinutes(50));
        getIncident(id).andExpect(jsonPath("$.sla.response.state").value("AT_RISK"));
        overdueList().andExpect(jsonPath("$.totalElements").value(0));
        stats().andExpect(jsonPath("$.slaBreached").value(0));

        clock.advance(Duration.ofMinutes(10));
        getIncident(id)
                .andExpect(jsonPath("$.sla.response.state").value("BREACHED"))
                .andExpect(jsonPath("$.sla.resolution.state").value("ON_TRACK"));
        overdueList()
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        stats().andExpect(jsonPath("$.slaBreached").value(1));

        // Picked up late: response stays breached, but it is no longer overdue.
        clock.advance(Duration.ofMinutes(5));
        patchJson("/api/incidents/{id}/assignee", id, "{\"assigneeId\": \"" + agentId + "\"}");

        getIncident(id)
                .andExpect(jsonPath("$.sla.response.state").value("BREACHED"))
                .andExpect(jsonPath("$.sla.response.completedAt").value("2026-01-05T10:05:00Z"));
        overdueList().andExpect(jsonPath("$.totalElements").value(0));
        stats().andExpect(jsonPath("$.slaBreached").value(0));
    }

    @Test
    void respondingOnTimeIsMetAndResolvingLateIsBreached() throws Exception {
        String id = createIncident("HIGH");

        clock.advance(Duration.ofMinutes(20));
        patchJson("/api/incidents/{id}/status", id, "{\"status\": \"IN_PROGRESS\"}");

        clock.advance(Duration.ofHours(8));
        getIncident(id)
                .andExpect(jsonPath("$.sla.response.state").value("MET"))
                .andExpect(jsonPath("$.sla.resolution.state").value("BREACHED"));
        overdueList().andExpect(jsonPath("$.totalElements").value(1));

        patchJson("/api/incidents/{id}/status", id, "{\"status\": \"RESOLVED\"}");

        getIncident(id)
                .andExpect(jsonPath("$.sla.resolution.state").value("BREACHED"))
                .andExpect(jsonPath("$.sla.resolution.completedAt").value("2026-01-05T17:20:00Z"));
        overdueList().andExpect(jsonPath("$.totalElements").value(0));
        stats().andExpect(jsonPath("$.slaBreached").value(0));
    }

    @Test
    void resolvingInTimeIsMet() throws Exception {
        String id = createIncident("CRITICAL");

        clock.advance(Duration.ofMinutes(10));
        patchJson("/api/incidents/{id}/status", id, "{\"status\": \"RESOLVED\"}");

        clock.advance(Duration.ofDays(2));
        getIncident(id)
                .andExpect(jsonPath("$.sla.response.state").value("MET"))
                .andExpect(jsonPath("$.sla.resolution.state").value("MET"));
    }

    @Test
    void raisingPriorityRecalculatesDeadlinesFromReportedTime() throws Exception {
        String id = createIncident("LOW");

        clock.advance(Duration.ofMinutes(30));
        getIncident(id).andExpect(jsonPath("$.sla.response.state").value("ON_TRACK"));

        patchJson("/api/incidents/{id}", id, "{\"priority\": \"CRITICAL\"}");

        getIncident(id)
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-05T09:15:00Z"))
                .andExpect(jsonPath("$.sla.response.state").value("BREACHED"))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-05T13:00:00Z"))
                .andExpect(jsonPath("$.sla.resolution.state").value("ON_TRACK"));
        overdueList().andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void overdueFilterCombinesWithOtherFilters() throws Exception {
        String high = createIncident("HIGH");
        createIncident("LOW");

        clock.advance(Duration.ofHours(2));

        overdueList().andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(high));
        mockMvc.perform(get("/api/incidents")
                        .param("overdue", "true")
                        .param("priority", "LOW"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/incidents"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
