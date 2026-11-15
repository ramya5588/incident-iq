package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
 * Reports over known data (UTC; HIGH respond 1h / resolve 8h, LOW 8h / 3d,
 * CRITICAL 15m / 4h). The report covers Mon 5 – Wed 7 January 2026 and is
 * run on Wednesday at 18:00:
 *
 * <pre>
 * A  HIGH  Mon 09:00  Hardware  picked up 09:30 (met)   resolved 12:00 (met, 3h)
 * B  HIGH  Mon 10:00  Hardware  picked up 11:30 (missed) resolved 20:00 (missed, 10h)
 * C  LOW   Tue 09:00  Network   never picked up (missed by Tue 17:00), resolve pending
 * D  HIGH  Wed 09:00  Network   on hold 09:10 (counts as picked up, met), assigned, paused
 * E  CRIT  Sun 23:00  (before the range) resolved Mon 01:00 (2h)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@Transactional
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    private UserAccount agent;
    private UserAccount reporter;
    private String databaseDown;
    private String laptopBroken;
    private String printerJam;

    @BeforeEach
    void setUp() throws Exception {
        agent = userRepository.saveAndFlush(
                new UserAccount("rep.agent@incidentiq.test", "Rae Agent", UserRole.AGENT));
        reporter = userRepository.saveAndFlush(
                new UserAccount("rep.user@incidentiq.test", "Rob Reporter", UserRole.USER));

        String e = report("2026-01-04T23:00:00Z", "CRITICAL", "Database down", null);
        databaseDown = e;
        at("2026-01-05T01:00:00Z");
        setStatus(e, "RESOLVED");

        String a = report("2026-01-05T09:00:00Z", "HIGH", "Laptop broken", "Hardware");
        at("2026-01-05T09:30:00Z");
        assign(a);
        String b = report("2026-01-05T10:00:00Z", "HIGH", "Printer jam", "Hardware");
        at("2026-01-05T11:30:00Z");
        assign(b);
        laptopBroken = a;
        printerJam = b;
        at("2026-01-05T12:00:00Z");
        setStatus(a, "RESOLVED");
        at("2026-01-05T20:00:00Z");
        setStatus(b, "RESOLVED");

        report("2026-01-06T09:00:00Z", "LOW", "Slow wifi", "Network");

        String d = report("2026-01-07T09:00:00Z", "HIGH", "VPN flaky", "Network");
        at("2026-01-07T09:10:00Z");
        mockMvc.perform(patch("/api/incidents/{id}/status", d)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ON_HOLD\", \"holdReason\": \"WAITING_ON_THIRD_PARTY\"}"))
                .andExpect(status().isOk());
        assign(d);

        at("2026-01-07T18:00:00Z");
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private void at(String instant) {
        clock.setInstant(Instant.parse(instant));
    }

    private String report(String when, String priority, String title, String category) throws Exception {
        at(when);
        TestAuth.signInAs(reporter);
        String categoryJson = category == null ? "null" : "\"" + category + "\"";
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "description": "Details", "priority": "%s", "category": %s}
                                """.formatted(title.replace("\"", "\\\""), priority, categoryJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        TestAuth.signInAs(agent);
        return JsonPath.read(body, "$.id");
    }

    private void assign(String id) throws Exception {
        mockMvc.perform(patch("/api/incidents/{id}/assignee", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + agent.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private void setStatus(String id, String value) throws Exception {
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions summary(String... params) throws Exception {
        var request = get("/api/reports/summary")
                .param("from", "2026-01-05").param("to", "2026-01-07");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private void rate(String id, int score, String comment) throws Exception {
        TestAuth.signInAs(reporter);
        String commentJson = comment == null ? "null" : "\"" + comment + "\"";
        mockMvc.perform(put("/api/incidents/{id}/rating", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\": " + score + ", \"comment\": " + commentJson + "}"))
                .andExpect(status().isOk());
        TestAuth.signInAs(agent);
    }

    @Test
    void reportsSatisfactionFromRatingsGivenInTheRange() throws Exception {
        summary().andExpect(jsonPath("$.satisfaction.ratings").value(0))
                .andExpect(jsonPath("$.satisfaction.average").doesNotExist());

        rate(laptopBroken, 5, null);
        rate(printerJam, 2, "Took ages");
        rate(databaseDown, 4, null);
        // Given after the range: not counted.
        at("2026-01-09T10:00:00Z");
        String late = report("2026-01-09T10:00:00Z", "LOW", "Mouse", "Hardware");
        setStatus(late, "RESOLVED");
        rate(late, 1, null);

        summary()
                .andExpect(jsonPath("$.satisfaction.ratings").value(3))
                .andExpect(jsonPath("$.satisfaction.average").value(3.7))
                .andExpect(jsonPath("$.satisfaction.satisfied").value(2));
        summary("priority", "HIGH")
                .andExpect(jsonPath("$.satisfaction.ratings").value(2))
                .andExpect(jsonPath("$.satisfaction.average").value(3.5))
                .andExpect(jsonPath("$.satisfaction.satisfied").value(1));

        String csv = mockMvc.perform(get("/api/reports/incidents.csv")
                        .param("from", "2026-01-05").param("to", "2026-01-07"))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.split("\r\n")[0]).endsWith(",Resolution SLA,Satisfaction,Feedback");
        assertThat(csv).contains("Printer jam").contains(",2,Took ages");
    }

    @Test
    void summarisesTheRange() throws Exception {
        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.reported").value(4))
                .andExpect(jsonPath("$.resolved").value(3))
                // Picked up after 30m, 90m and 10m.
                .andExpect(jsonPath("$.medianResponseSeconds").value(1800))
                // Resolved in the range after 3h, 10h and 2h (E was reported before it).
                .andExpect(jsonPath("$.medianResolveSeconds").value(10800))
                .andExpect(jsonPath("$.responseSla.met").value(2))
                .andExpect(jsonPath("$.responseSla.missed").value(2))
                // C is still running and D is paused, so neither counts yet.
                .andExpect(jsonPath("$.resolutionSla.met").value(1))
                .andExpect(jsonPath("$.resolutionSla.missed").value(1))
                .andExpect(jsonPath("$.overdueNow").value(1));
    }

    @Test
    void breaksItDownByDayPriorityCategoryAndAssignee() throws Exception {
        summary()
                .andExpect(jsonPath("$.daily.length()").value(3))
                .andExpect(jsonPath("$.daily[0].date").value("2026-01-05"))
                .andExpect(jsonPath("$.daily[0].reported").value(2))
                .andExpect(jsonPath("$.daily[0].resolved").value(3))
                .andExpect(jsonPath("$.daily[1].reported").value(1))
                .andExpect(jsonPath("$.daily[2].reported").value(1))
                .andExpect(jsonPath("$.daily[2].resolved").value(0))
                .andExpect(jsonPath("$.byPriority[*].priority",
                        Matchers.contains("LOW", "MEDIUM", "HIGH", "CRITICAL")))
                .andExpect(jsonPath("$.byPriority[0].response.missed").value(1))
                .andExpect(jsonPath("$.byPriority[2].reported").value(3))
                .andExpect(jsonPath("$.byPriority[2].response.met").value(2))
                .andExpect(jsonPath("$.byPriority[2].response.missed").value(1))
                .andExpect(jsonPath("$.byPriority[2].resolution.met").value(1))
                .andExpect(jsonPath("$.byPriority[3].reported").value(0))
                .andExpect(jsonPath("$.topCategories[*].category", Matchers.contains("Hardware", "Network")))
                .andExpect(jsonPath("$.topCategories[0].count").value(2))
                .andExpect(jsonPath("$.workload.unassigned").value(1))
                .andExpect(jsonPath("$.workload.assignees.length()").value(1))
                .andExpect(jsonPath("$.workload.assignees[0].name").value("Rae Agent"))
                .andExpect(jsonPath("$.workload.assignees[0].onHold").value(1));
    }

    @Test
    void canBeLimitedToOnePriority() throws Exception {
        summary("priority", "HIGH")
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.reported").value(3))
                .andExpect(jsonPath("$.resolved").value(2))
                .andExpect(jsonPath("$.overdueNow").value(0))
                .andExpect(jsonPath("$.byPriority.length()").value(1))
                .andExpect(jsonPath("$.workload.unassigned").value(0));
    }

    @Test
    void countsDaysInTheViewersTimeZone() throws Exception {
        // E (Sun 23:00 UTC) is Monday 08:00 in Tokyo, so it falls in the range there.
        summary("timeZone", "Asia/Tokyo")
                .andExpect(jsonPath("$.timeZone").value("Asia/Tokyo"))
                .andExpect(jsonPath("$.reported").value(5))
                .andExpect(jsonPath("$.daily[0].reported").value(3));
    }

    @Test
    void checksTheRequest() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("from", "2026-01-07").param("to", "2026-01-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The start date must not be after the end date."));
        mockMvc.perform(get("/api/reports/summary").param("from", "2025-01-01").param("to", "2026-01-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reports cover at most 366 days."));
        summary("timeZone", "+02:00").andExpect(status().isBadRequest());
        summary("timeZone", "Mars/Olympus")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown time zone: Mars/Olympus"));
        summary("priority", "URGENT").andExpect(status().isBadRequest());
    }

    @Test
    void reportsAreForAgentsAndAdmins() throws Exception {
        TestAuth.signInAs(reporter);
        summary().andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/incidents.csv").param("from", "2026-01-05").param("to", "2026-01-07"))
                .andExpect(status().isForbidden());
        TestAuth.signOut();
        summary().andExpect(status().isUnauthorized());
    }

    @Test
    void exportsTheIncidentsAsCsv() throws Exception {
        report("2026-01-07T10:00:00Z", "LOW", "=HYPERLINK(\"http://evil.example\", \"Click\")", "Misc");
        at("2026-01-07T18:00:00Z");

        String csv = mockMvc.perform(get("/api/reports/incidents.csv")
                        .param("from", "2026-01-05").param("to", "2026-01-07").param("timeZone", "Europe/London"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("incidents-2026-01-05-to-2026-01-07.csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String[] lines = csv.replace("﻿", "").split("\r\n");
        assertThat(lines).hasSize(6);
        assertThat(lines[0]).startsWith("Incident,Title,Priority,Status,Category,Reported by,Assignee,Reported,");
        // Oldest first; London is on GMT in January.
        assertThat(lines[1]).contains(",Laptop broken,HIGH,RESOLVED,Hardware,Rob Reporter,Rae Agent,2026-01-05 09:00,")
                .endsWith(",Met,2026-01-05 17:00,2026-01-05 12:00,Met,,");
        assertThat(lines[2]).contains("Printer jam").endsWith(",Missed,,");
        assertThat(lines[3]).contains("Slow wifi,LOW,OPEN,Network,Rob Reporter,,").contains(",Missed,")
                .endsWith(",Pending,,");
        assertThat(lines[4]).contains("VPN flaky").endsWith(",Paused,,");
        // A title that looks like a formula is neutralised and quoted.
        assertThat(lines[5]).contains(",\"'=HYPERLINK(\"\"http://evil.example\"\", \"\"Click\"\")\",");
    }

    @Test
    void csvCellsAreQuotedSafely() {
        assertThat(ReportService.cell("plain")).isEqualTo("plain");
        assertThat(ReportService.cell("a, b")).isEqualTo("\"a, b\"");
        assertThat(ReportService.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportService.cell("+1 555")).isEqualTo("\"'+1 555\"");
        assertThat(ReportService.cell("@cmd")).isEqualTo("\"'@cmd\"");
        assertThat(ReportService.cell(null)).isEmpty();
    }
}
