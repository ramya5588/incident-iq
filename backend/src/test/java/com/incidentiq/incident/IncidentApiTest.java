package com.incidentiq.incident;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
