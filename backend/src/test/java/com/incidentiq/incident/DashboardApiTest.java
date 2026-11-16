package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@Transactional
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    private UserAccount riley;
    private UserAccount alex;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        riley = userRepository.saveAndFlush(new UserAccount("dash.riley@incidentiq.test", "Riley Reporter", UserRole.USER));
        alex = userRepository.saveAndFlush(new UserAccount("dash.alex@incidentiq.test", "Alex Agent", UserRole.AGENT));
        admin = userRepository.saveAndFlush(new UserAccount("dash.ada@incidentiq.test", "Ada Admin", UserRole.ADMIN));
        at("2026-03-02T09:00:00Z");
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private void at(String instant) {
        clock.setInstant(Instant.parse(instant));
    }

    private String report(String title, String priority) throws Exception {
        TestAuth.signInAs(riley);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "description": "Broken", "priority": "%s", "category": "IT"}
                                """.formatted(title, priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void assignToAlex(String id) throws Exception {
        TestAuth.signInAs(admin);
        mockMvc.perform(patch("/api/incidents/{id}/assignee", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + alex.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private void setStatus(UserAccount as, String id, String json) throws Exception {
        TestAuth.signInAs(as);
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private ResultActions myWork(UserAccount as) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(get("/api/dashboard/my-work"));
    }

    @Test
    void myWorkShowsWhatNeedsDoingNext() throws Exception {
        long queueBefore = ((Number) JsonPath.read(myWork(alex).andReturn().getResponse().getContentAsString(),
                "$.unassignedTotal")).longValue();

        String printer = report("Printer jam", "LOW");
        String outage = report("Email down", "HIGH");
        String laptop = report("Laptop slow", "MEDIUM");
        String vpn = report("VPN drops", "MEDIUM");
        assignToAlex(printer);
        assignToAlex(outage);
        assignToAlex(vpn);
        setStatus(alex, vpn, "{\"status\": \"ON_HOLD\", \"holdReason\": \"WAITING_ON_REPORTER\"}");

        myWork(alex)
                .andExpect(status().isOk())
                // The high-priority one is due first, though it was reported later.
                .andExpect(jsonPath("$.dueSoon[*].title", Matchers.contains("Email down", "Printer jam")))
                .andExpect(jsonPath("$.dueSoon[0].sla").exists())
                .andExpect(jsonPath("$.waiting[*].title", Matchers.contains("VPN drops")))
                .andExpect(jsonPath("$.waiting[0].holdReason").value("WAITING_ON_REPORTER"))
                .andExpect(jsonPath("$.unassigned[*].id", Matchers.hasItem(laptop)))
                .andExpect(jsonPath("$.unassignedTotal").value(queueBefore + 1))
                // Alex was assigned three: newest notification first, one per incident.
                .andExpect(jsonPath("$.unread[*].title", Matchers.contains("VPN drops", "Email down", "Printer jam")))
                .andExpect(jsonPath("$.unread[0].type").value("ASSIGNED"));

        // Taking one from the queue moves it to "due soon".
        TestAuth.signInAs(alex);
        mockMvc.perform(patch("/api/incidents/{id}/assignee", laptop)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + alex.getId() + "\"}"))
                .andExpect(status().isOk());
        myWork(alex)
                .andExpect(jsonPath("$.dueSoon[*].id", Matchers.hasItem(laptop)))
                .andExpect(jsonPath("$.unassignedTotal").value(queueBefore));
    }

    @Test
    void reportersSeeTheirOwnIncidents() throws Exception {
        String first = report("Printer jam", "LOW");
        at("2026-03-02T10:00:00Z");
        String second = report("VPN drops", "MEDIUM");
        at("2026-03-02T11:00:00Z");
        setStatus(admin, first, "{\"status\": \"IN_PROGRESS\"}");

        TestAuth.signInAs(riley);
        mockMvc.perform(get("/api/dashboard/my-incidents"))
                .andExpect(status().isOk())
                // Most recently changed first.
                .andExpect(jsonPath("$[*].id", Matchers.contains(first, second)))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));

        myWork(riley).andExpect(status().isForbidden());
        TestAuth.signInAs(alex);
        mockMvc.perform(get("/api/dashboard/my-incidents")).andExpect(jsonPath("$.length()").value(0));
    }

    private List<Integer> trend() throws Exception {
        TestAuth.signInAs(alex);
        return JsonPath.read(mockMvc.perform(get("/api/reports/open-trend")
                        .param("days", "3").param("timeZone", "UTC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[*].open");
    }

    @Test
    void theTrendCountsIncidentsOpenAtTheEndOfEachDay() throws Exception {
        at("2026-03-04T12:00:00Z");
        List<Integer> before = trend();

        at("2026-03-02T09:00:00Z");
        String network = report("Network down", "HIGH");
        at("2026-03-03T09:00:00Z");
        report("Printer jam", "LOW");
        at("2026-03-04T10:00:00Z");
        setStatus(admin, network, "{\"status\": \"RESOLVED\"}");

        at("2026-03-04T12:00:00Z");
        List<Integer> after = trend();
        // Mar 2: network. Mar 3: network and printer. Today so far: just the printer.
        assertThat(List.of(after.get(0) - before.get(0), after.get(1) - before.get(1), after.get(2) - before.get(2)))
                .containsExactly(1, 2, 1);

        TestAuth.signInAs(alex);
        mockMvc.perform(get("/api/reports/open-trend").param("days", "3").param("timeZone", "UTC"))
                .andExpect(jsonPath("$[0].date").value("2026-03-02"))
                .andExpect(jsonPath("$[2].date").value("2026-03-04"));
        mockMvc.perform(get("/api/reports/open-trend").param("days", "91"))
                .andExpect(status().isBadRequest());
        TestAuth.signInAs(riley);
        mockMvc.perform(get("/api/reports/open-trend")).andExpect(status().isForbidden());
    }
}
