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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end API tests against a throwaway PostgreSQL container.
 * Each test runs in a transaction that is rolled back afterwards, signed in
 * as a fresh admin unless it switches user with {@link #actAs}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class IncidentApiTest {

    private static final UUID UNKNOWN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    private String adminId;

    @BeforeEach
    void signInAsAdmin() {
        UserAccount admin = userRepository.saveAndFlush(new UserAccount(
                "admin." + UUID.randomUUID() + "@incidentiq.test",
                "Test Admin",
                UserRole.ADMIN));
        adminId = admin.getId().toString();
        TestAuth.signInAs(admin);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    // --- helpers -----------------------------------------------------------

    /** Makes later requests in this test run as the given user. */
    private void actAs(String userId) {
        TestAuth.signInAs(userRepository.findById(UUID.fromString(userId)).orElseThrow());
    }

    private String createUser(String name, String role) throws Exception {
        String email = name.toLowerCase().replace(' ', '.')
                + "." + UUID.randomUUID() + "@incidentiq.test";

        String body = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "%s", "role": "%s",
                                 "password": "test-password"}
                                """.formatted(email, name, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.id");
    }

    /** Creates an incident reported by {@code creatorId}, then switches back. */
    private String createIncident(String title, String priority, String creatorId)
            throws Exception {

        Authentication previous = TestAuth.current();
        actAs(creatorId);

        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Test description",
                                  "priority": "%s",
                                  "category": "Testing"
                                }
                                """.formatted(title, priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        TestAuth.signIn(previous);

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

    // --- activity timeline -------------------------------------------------

    private void setAssignee(String incidentId, String assigneeId) throws Exception {
        String value = assigneeId == null ? "null" : "\"" + assigneeId + "\"";
        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": " + value + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void newIncidentHasCreatedEventByCreator() throws Exception {
        String creatorId = createUser("Casey Creator", "USER");
        String incidentId = createIncident("Timeline start", "LOW", creatorId);

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("CREATED"))
                .andExpect(jsonPath("$[0].actorId").value(creatorId))
                .andExpect(jsonPath("$[0].actorName").value("Casey Creator"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void timelineRecordsChangesNewestFirst() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String alexId = createUser("Alex Agent", "AGENT");
        String samId = createUser("Sam Agent", "AGENT");
        String incidentId = createIncident("Busy incident", "HIGH", creatorId);

        setStatus(incidentId, "IN_PROGRESS");
        setAssignee(incidentId, alexId);
        setAssignee(incidentId, samId);
        mockMvc.perform(post("/api/incidents/{id}/triage", incidentId))
                .andExpect(status().isOk());
        setAssignee(incidentId, null);
        setStatus(incidentId, "RESOLVED");

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].fromValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].toValue").value("RESOLVED"))
                .andExpect(jsonPath("$[1].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[1].fromValue").value("Sam Agent"))
                .andExpect(jsonPath("$[1].toValue").value(nullValue()))
                .andExpect(jsonPath("$[2].type").value("AI_TRIAGED"))
                .andExpect(jsonPath("$[2].message").isNotEmpty())
                .andExpect(jsonPath("$[3].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[3].fromValue").value("Alex Agent"))
                .andExpect(jsonPath("$[3].toValue").value("Sam Agent"))
                .andExpect(jsonPath("$[4].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[4].fromValue").value(nullValue()))
                .andExpect(jsonPath("$[4].toValue").value("Alex Agent"))
                .andExpect(jsonPath("$[5].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[5].fromValue").value("OPEN"))
                .andExpect(jsonPath("$[5].toValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[6].type").value("CREATED"));
    }

    @Test
    void unchangedValuesDoNotAddEvents() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String agentId = createUser("Alex Agent", "AGENT");
        String incidentId = createIncident("No-op updates", "LOW", creatorId);

        setStatus(incidentId, "OPEN");
        setAssignee(incidentId, null);
        setAssignee(incidentId, agentId);
        setAssignee(incidentId, agentId);

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[1].type").value("CREATED"));
    }

    @Test
    void addsTrimmedCommentToTimeline() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String agentId = createUser("Alex Agent", "AGENT");
        String incidentId = createIncident("Needs notes", "MEDIUM", creatorId);

        actAs(agentId);
        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "  Rolled back deploy 4.2  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COMMENT"))
                .andExpect(jsonPath("$.message").value("Rolled back deploy 4.2"))
                .andExpect(jsonPath("$.actorName").value("Alex Agent"))
                .andExpect(jsonPath("$.id").isNumber());

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("COMMENT"))
                .andExpect(jsonPath("$[0].message").value("Rolled back deploy 4.2"));
    }

    @Test
    void rejectsInvalidComments() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Validation", "LOW", creatorId);

        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void eventsAndCommentsOnUnknownIncidentReturn404() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}/events", UNKNOWN_ID))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/incidents/{id}/comments", UNKNOWN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Hi\"}"))
                .andExpect(status().isNotFound());
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

    // --- acting user (from the session) -----------------------------------

    @Test
    void changesAreAttributedToTheSignedInUser() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String alexId = createUser("Alex Agent", "AGENT");
        String samId = createUser("Sam Agent", "AGENT");
        String incidentId = createIncident("Attributed", "HIGH", creatorId);

        actAs(alexId);
        setStatus(incidentId, "IN_PROGRESS");

        actAs(samId);
        setAssignee(incidentId, alexId);

        actAs(alexId);
        mockMvc.perform(post("/api/incidents/{id}/triage", incidentId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].type").value("AI_TRIAGED"))
                .andExpect(jsonPath("$[0].actorId").value(alexId))
                .andExpect(jsonPath("$[0].actorName").value("Alex Agent"))
                .andExpect(jsonPath("$[1].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[1].actorId").value(samId))
                .andExpect(jsonPath("$[1].actorName").value("Sam Agent"))
                .andExpect(jsonPath("$[1].toValue").value("Alex Agent"))
                .andExpect(jsonPath("$[2].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[2].actorId").value(alexId))
                .andExpect(jsonPath("$[3].actorId").value(creatorId));
    }

    @Test
    void clientCannotChooseReporterAuthorOrActor() throws Exception {
        String alexId = createUser("Alex Agent", "AGENT");
        String samId = createUser("Sam Agent", "AGENT");
        actAs(alexId);

        // Old-style fields and the old header are ignored: Alex is the actor.
        String body = mockMvc.perform(post("/api/incidents")
                        .header("X-User-Id", samId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Spoof", "description": "Who?",
                                 "category": "Testing", "createdById": "%s"}
                                """.formatted(samId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdById").value(alexId))
                .andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(body, "$.id");

        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorId\": \"" + samId + "\", \"body\": \"Hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actorId").value(alexId));

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .header("X-User-Id", samId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].actorId").value(alexId));
    }

    @Test
    void signedOutRequestsAreRejected() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Private", "LOW", creatorId);
        TestAuth.signOut();

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());

        actAs(adminId);
        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    // --- editing details ---------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions editIncident(
            String incidentId, String json, String actorId) throws Exception {

        var request = patch("/api/incidents/{id}", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (actorId != null) {
            actAs(actorId);
        }
        return mockMvc.perform(request);
    }

    @Test
    void editingUpdatesFieldsAndRecordsOneEventPerChange() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String alexId = createUser("Alex Agent", "AGENT");
        String incidentId = createIncident("Login slow", "LOW", creatorId);

        editIncident(incidentId, """
                {
                  "title": "  Login very slow  ",
                  "description": "Takes 30s for everyone",
                  "priority": "CRITICAL",
                  "category": "Performance"
                }
                """, alexId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Login very slow"))
                .andExpect(jsonPath("$.description").value("Takes 30s for everyone"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.category").value("Performance"));

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].type").value("CATEGORY_CHANGED"))
                .andExpect(jsonPath("$[0].fromValue").value("Testing"))
                .andExpect(jsonPath("$[0].toValue").value("Performance"))
                .andExpect(jsonPath("$[1].type").value("PRIORITY_CHANGED"))
                .andExpect(jsonPath("$[1].fromValue").value("LOW"))
                .andExpect(jsonPath("$[1].toValue").value("CRITICAL"))
                .andExpect(jsonPath("$[2].type").value("DESCRIPTION_EDITED"))
                .andExpect(jsonPath("$[2].fromValue").value(nullValue()))
                .andExpect(jsonPath("$[3].type").value("TITLE_CHANGED"))
                .andExpect(jsonPath("$[3].fromValue").value("Login slow"))
                .andExpect(jsonPath("$[3].toValue").value("Login very slow"))
                .andExpect(jsonPath("$[3].actorName").value("Alex Agent"))
                .andExpect(jsonPath("$[4].type").value("CREATED"));
    }

    @Test
    void partialEditLeavesOtherFieldsAndSkipsUnchanged() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Printer jam", "MEDIUM", creatorId);

        editIncident(incidentId, """
                {"title": "Printer jam", "priority": "HIGH"}
                """, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Printer jam"))
                .andExpect(jsonPath("$.description").value("Test description"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.category").value("Testing"));

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("PRIORITY_CHANGED"));

        editIncident(incidentId, "{}", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void blankCategoryClearsIt() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Uncategorised", "LOW", creatorId);

        editIncident(incidentId, "{\"category\": \"   \"}", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value(nullValue()));

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$[0].type").value("CATEGORY_CHANGED"))
                .andExpect(jsonPath("$[0].fromValue").value("Testing"))
                .andExpect(jsonPath("$[0].toValue").value(nullValue()));
    }

    @Test
    void invalidEditIsRejectedAndChangesNothing() throws Exception {
        String creatorId = createUser("Creator", "USER");
        String incidentId = createIncident("Keep me", "LOW", creatorId);

        editIncident(incidentId, """
                {"title": "   ", "description": "", "priority": "HIGH"}
                """, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").value("Title must not be blank"))
                .andExpect(jsonPath("$.details.description").exists());

        editIncident(incidentId,
                "{\"title\": \"" + "x".repeat(201) + "\"}", null)
                .andExpect(status().isBadRequest());

        editIncident(incidentId, "{\"priority\": \"URGENT\"}", null)
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(jsonPath("$.title").value("Keep me"))
                .andExpect(jsonPath("$.priority").value("LOW"));
    }

    @Test
    void editingUnknownIncidentReturns404() throws Exception {
        editIncident(UNKNOWN_ID.toString(), "{\"title\": \"Nope\"}", null)
                .andExpect(status().isNotFound());
    }

    // --- roles -------------------------------------------------------------

    @Test
    void regularUsersCanReportViewAndCommentButNotWorkIncidents() throws Exception {
        String reporterId = createUser("Riley Reporter", "USER");
        String agentId = createUser("Alex Agent", "AGENT");
        String incidentId = createIncident("Laptop won't boot", "MEDIUM", reporterId);
        actAs(reporterId);

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Still broken\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You don't have permission to do that."));
        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + agentId + "\"}"))
                .andExpect(status().isForbidden());
        editIncident(incidentId, "{\"priority\": \"CRITICAL\"}", null)
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/incidents/{id}/triage", incidentId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.assignedToId").value(nullValue()))
                .andExpect(jsonPath("$.aiSummary").value(nullValue()));
        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("COMMENT"));
    }

    @Test
    void agentsCanWorkIncidents() throws Exception {
        String reporterId = createUser("Riley Reporter", "USER");
        String agentId = createUser("Alex Agent", "AGENT");
        String incidentId = createIncident("Agent work", "LOW", reporterId);
        actAs(agentId);

        setStatus(incidentId, "IN_PROGRESS");
        setAssignee(incidentId, agentId);
        editIncident(incidentId, "{\"priority\": \"HIGH\"}", null)
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/incidents/{id}/triage", incidentId))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAgentsAndAdminsCanBeAssigned() throws Exception {
        String reporterId = createUser("Riley Reporter", "USER");
        String incidentId = createIncident("Who takes it?", "LOW", reporterId);

        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + reporterId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Riley Reporter can't be assigned incidents: only agents and admins can."));

        setAssignee(incidentId, adminId);

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].toValue").value("Test Admin"));
    }
}
