package com.incidentiq.incident;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end API tests against a throwaway PostgreSQL container.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class IncidentApiTest {

    private static final UUID UNKNOWN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    private MockMvc mockMvc;

    // --- helpers -----------------------------------------------------------

    private String createUser(String name, String role) throws Exception {
        String email = name.toLowerCase().replace(' ', '.')
                + "." + UUID.randomUUID() + "@incidentiq.test";

        String body = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "%s", "role": "%s"}
                                """.formatted(email, name, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.id");
    }

    private String createIncident(String title, String priority, String creatorId)
            throws Exception {

        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Test description",
                                  "priority": "%s",
                                  "category": "Testing",
                                  "createdById": "%s"
                                }
                                """.formatted(title, priority, creatorId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.id");
    }

    private void setStatus(String incidentId, String newStatus) throws Exception {
        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + newStatus + "\"}"))
                .andExpect(status().isOk());
    }

    // --- creation ----------------------------------------------------------

    @Test
    void createdIncidentIsOpenAndUnassigned() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Login broken", "HIGH", creatorId);

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.createdById").value(creatorId))
                .andExpect(jsonPath("$.assignedToId").value(nullValue()))
                .andExpect(jsonPath("$.incidentNumber").isNumber());
    }

    @Test
    void unknownIncidentReturns404() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}", UNKNOWN_ID))
                .andExpect(status().isNotFound());
    }

    // --- status ------------------------------------------------------------

    @Test
    void resolvingSetsResolvedAtAndReopeningClearsIt() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Disk full", "MEDIUM", creatorId);

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()));
    }

    @Test
    void missingStatusIsRejected() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Bad request", "LOW", creatorId);

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusUpdateOnUnknownIncidentReturns404() throws Exception {
        mockMvc.perform(patch("/api/incidents/{id}/status", UNKNOWN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isNotFound());
    }

    // --- assignment --------------------------------------------------------

    @Test
    void assigningAndUnassigningAnIncident() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String agentId = createUser("Alex Agent", "AGENT");
        String incidentId = createIncident("VPN flaky", "MEDIUM", creatorId);

        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + agentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(agentId))
                .andExpect(jsonPath("$.assignedToName").value("Alex Agent"));

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(jsonPath("$.assignedToName").value("Alex Agent"));

        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(nullValue()))
                .andExpect(jsonPath("$.assignedToName").value(nullValue()));
    }

    @Test
    void assigningUnknownUserReturns404() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Printer on fire", "CRITICAL", creatorId);

        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + UNKNOWN_ID + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(jsonPath("$.assignedToId").value(nullValue()));
    }

    @Test
    void assigningOnUnknownIncidentReturns404() throws Exception {
        String agentId = createUser("Alex Agent", "AGENT");

        mockMvc.perform(patch("/api/incidents/{id}/assignee", UNKNOWN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + agentId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // --- filtering ---------------------------------------------------------

    @Test
    void filtersByStatusPriorityAndBothTogether() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String openHigh = createIncident("Open high", "HIGH", creatorId);
        String openLow = createIncident("Open low", "LOW", creatorId);
        String resolvedHigh = createIncident("Resolved high", "HIGH", creatorId);
        setStatus(resolvedHigh, "RESOLVED");

        mockMvc.perform(get("/api/incidents").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id",
                        containsInAnyOrder(openHigh, openLow)));

        mockMvc.perform(get("/api/incidents").param("priority", "HIGH"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id",
                        containsInAnyOrder(openHigh, resolvedHigh)));

        mockMvc.perform(get("/api/incidents")
                        .param("status", "OPEN")
                        .param("priority", "HIGH"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(openHigh));

        mockMvc.perform(get("/api/incidents")
                        .param("status", "RESOLVED")
                        .param("priority", "LOW"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- stats -------------------------------------------------------------

    @Test
    void statsAreZeroWithNoIncidents() throws Exception {
        mockMvc.perform(get("/api/incidents/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.open").value(0))
                .andExpect(jsonPath("$.resolvedInWindow").value(0))
                .andExpect(jsonPath("$.aiTriagedPercentage").value(0))
                .andExpect(jsonPath("$.resolvedSince").isNotEmpty());
    }

    @Test
    void statsCountAcrossAllIncidentsNotJustOnePage() throws Exception {
        String creatorId = createUser("Creator", "USER");
        // More than the default list page size (20) to prove counts aren't page-limited
        for (int i = 0; i < 22; i++) {
            createIncident("Open " + i, "LOW", creatorId);
        }
        String inProgress = createIncident("Working on it", "MEDIUM", creatorId);
        setStatus(inProgress, "IN_PROGRESS");
        String resolved = createIncident("Fixed", "HIGH", creatorId);
        setStatus(resolved, "RESOLVED");
        String closed = createIncident("Done", "HIGH", creatorId);
        setStatus(closed, "CLOSED");

        mockMvc.perform(post("/api/incidents/{id}/triage", resolved))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/incidents/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.open").value(22))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.resolved").value(1))
                .andExpect(jsonPath("$.closed").value(1))
                .andExpect(jsonPath("$.resolvedInWindow").value(1))
                .andExpect(jsonPath("$.aiTriaged").value(1))
                .andExpect(jsonPath("$.aiTriagedPercentage").value(4));
    }

    @Test
    void resolvedInWindowRespectsResolvedSince() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Resolved now", "HIGH", creatorId);
        setStatus(incidentId, "RESOLVED");

        Instant now = Instant.now();

        mockMvc.perform(get("/api/incidents/stats")
                        .param("resolvedSince", now.minus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.resolvedInWindow").value(1));

        mockMvc.perform(get("/api/incidents/stats")
                        .param("resolvedSince", now.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.resolvedInWindow").value(0));
    }

    @Test
    void reopenedIncidentLeavesResolvedWindow() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Flapping", "HIGH", creatorId);
        setStatus(incidentId, "RESOLVED");
        setStatus(incidentId, "OPEN");

        mockMvc.perform(get("/api/incidents/stats"))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.resolvedInWindow").value(0));
    }

    @Test
    void invalidResolvedSinceIsRejected() throws Exception {
        mockMvc.perform(get("/api/incidents/stats").param("resolvedSince", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    // --- workload ----------------------------------------------------------

    @Test
    void workloadCountsOnlyActiveAssignedIncidents() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String alexId = createUser("Alex Agent", "AGENT");
        String samId = createUser("Sam Agent", "AGENT");

        String open = createIncident("Open", "LOW", creatorId);
        String inProgress = createIncident("In progress", "LOW", creatorId);
        String resolved = createIncident("Resolved", "LOW", creatorId);
        String samsResolved = createIncident("Sam resolved", "LOW", creatorId);
        createIncident("Unassigned", "LOW", creatorId);

        for (String id : new String[] {open, inProgress, resolved}) {
            mockMvc.perform(patch("/api/incidents/{id}/assignee", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeId\": \"" + alexId + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(patch("/api/incidents/{id}/assignee", samsResolved)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + samId + "\"}"))
                .andExpect(status().isOk());

        setStatus(inProgress, "IN_PROGRESS");
        setStatus(resolved, "RESOLVED");
        setStatus(samsResolved, "RESOLVED");

        mockMvc.perform(get("/api/incidents/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(alexId))
                .andExpect(jsonPath("$[0].activeCount").value(2));
    }

    @Test
    void paginatesResults() throws Exception {
        String creatorId = createUser("Creator", "USER");
        createIncident("First", "LOW", creatorId);
        createIncident("Second", "LOW", creatorId);
        createIncident("Third", "LOW", creatorId);

        mockMvc.perform(get("/api/incidents").param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/incidents").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1));
    }
}
