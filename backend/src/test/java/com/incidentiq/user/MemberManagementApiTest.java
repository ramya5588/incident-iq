package com.incidentiq.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.notification.NotificationRepository;
import com.jayway.jsonpath.JsonPath;

/** Editing, deactivating and reactivating team members. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class MemberManagementApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserAccount admin;
    private UserAccount agent;
    private UserAccount reporter;

    @BeforeEach
    void setUp() {
        admin = save("ada@incidentiq.test", "Ada Admin", UserRole.ADMIN);
        agent = save("alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        reporter = save("riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        TestAuth.signInAs(admin);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private ResultActions editUser(UUID id, String json) throws Exception {
        return mockMvc.perform(patch("/api/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions deactivate(UUID id, boolean unassign) throws Exception {
        return mockMvc.perform(post("/api/users/{id}/deactivate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unassignOpenIncidents\": " + unassign + "}"));
    }

    private String createIncident(UserAccount as) throws Exception {
        TestAuth.signInAs(as);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Printer jam", "description": "Tray 2",
                                 "priority": "LOW", "category": "Hardware"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        TestAuth.signInAs(admin);
        return JsonPath.read(body, "$.id");
    }

    private ResultActions assign(String incidentId, UUID assigneeId) throws Exception {
        return mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": \"" + assigneeId + "\"}"));
    }

    // --- editing -----------------------------------------------------------

    @Test
    void adminCanEditNameAndEmail() throws Exception {
        editUser(agent.getId(), """
                {"displayName": "  Alexandra Agent ", "email": " Alexandra@IncidentIQ.test "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alexandra Agent"))
                .andExpect(jsonPath("$.email").value("alexandra@incidentiq.test"))
                .andExpect(jsonPath("$.role").value("AGENT"));

        // Omitted fields stay as they are.
        editUser(agent.getId(), "{\"displayName\": \"Alex A.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alexandra@incidentiq.test"));

        // Same email in a different case is not a conflict with yourself.
        editUser(agent.getId(), "{\"email\": \"ALEXANDRA@incidentiq.test\"}")
                .andExpect(status().isOk());
    }

    @Test
    void editingValidatesAndKeepsEmailsUnique() throws Exception {
        editUser(agent.getId(), "{\"email\": \"RILEY@incidentiq.test\"}")
                .andExpect(status().isConflict());
        editUser(agent.getId(), "{\"displayName\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.displayName").value("Display name must not be blank"));
        editUser(agent.getId(), "{\"email\": \"not-an-email\"}")
                .andExpect(status().isBadRequest());
        editUser(UUID.randomUUID(), "{\"displayName\": \"Ghost\"}")
                .andExpect(status().isNotFound());

        assertThat(userRepository.findById(agent.getId()).orElseThrow().getEmail())
                .isEqualTo("alex@incidentiq.test");
    }

    // --- deactivating ------------------------------------------------------

    @Test
    void deactivatingCanReturnOpenIncidentsToTheQueue() throws Exception {
        String open = createIncident(reporter);
        String inProgress = createIncident(reporter);
        String resolved = createIncident(reporter);
        for (String id : new String[] {open, inProgress, resolved}) {
            assign(id, agent.getId()).andExpect(status().isOk());
        }
        mockMvc.perform(patch("/api/incidents/{id}/status", inProgress)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"IN_PROGRESS\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/incidents/{id}/status", resolved)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"RESOLVED\"}")).andExpect(status().isOk());

        deactivate(agent.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.active").value(false))
                .andExpect(jsonPath("$.user.deactivatedAt").isNotEmpty())
                .andExpect(jsonPath("$.unassignedIncidents").value(2));

        mockMvc.perform(get("/api/incidents/{id}", open))
                .andExpect(jsonPath("$.assignedToId").doesNotExist());
        mockMvc.perform(get("/api/incidents/{id}", inProgress))
                .andExpect(jsonPath("$.assignedToId").doesNotExist());
        // History is kept: the resolved incident still shows who handled it.
        mockMvc.perform(get("/api/incidents/{id}", resolved))
                .andExpect(jsonPath("$.assignedToName").value("Alex Agent"));
        mockMvc.perform(get("/api/incidents/{id}/events", open))
                .andExpect(jsonPath("$[0].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[0].fromValue").value("Alex Agent"))
                .andExpect(jsonPath("$[0].actorName").value("Ada Admin"));
    }

    @Test
    void deactivatedMembersCantBeAssignedOrNotified() throws Exception {
        String id = createIncident(reporter);
        assign(id, agent.getId()).andExpect(status().isOk());
        long before = notificationRepository.countByRecipientIdAndReadAtIsNull(agent.getId());

        deactivate(agent.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedIncidents").value(0));

        // Left assigned when not asked to unassign...
        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(jsonPath("$.assignedToName").value("Alex Agent"));

        // ...but gets no new notifications, and can't be given new work.
        TestAuth.signInAs(reporter);
        mockMvc.perform(post("/api/incidents/{id}/comments", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \"Any update?\"}")).andExpect(status().isCreated());
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(agent.getId()))
                .isEqualTo(before);

        TestAuth.signInAs(admin);
        String another = createIncident(reporter);
        assign(another, agent.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Alex Agent is deactivated and can't be assigned incidents."));
    }

    @Test
    void deactivatedMembersAreListedAndCanBeReactivated() throws Exception {
        deactivate(agent.getId(), false).andExpect(status().isOk());
        // Deactivating twice is harmless.
        deactivate(agent.getId(), false).andExpect(status().isOk());

        mockMvc.perform(get("/api/users"))
                .andExpect(jsonPath("$[?(@.displayName == 'Alex Agent')].active").value(false));

        mockMvc.perform(post("/api/users/{id}/reactivate", agent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.deactivatedAt").doesNotExist());

        String id = createIncident(reporter);
        assign(id, agent.getId()).andExpect(status().isOk());
    }

    @Test
    void youCantDeactivateYourself() throws Exception {
        deactivate(admin.getId(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can't deactivate your own account."));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void anotherAdminCanBeDeactivatedWhileOneRemains() throws Exception {
        UserAccount second = save("sam@incidentiq.test", "Sam Admin", UserRole.ADMIN);

        deactivate(second.getId(), false).andExpect(status().isOk());

        // With Sam deactivated, Ada is the only active admin: no demoting her.
        mockMvc.perform(patch("/api/users/{id}/role", admin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"AGENT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAdminsCanEditOrDeactivate() throws Exception {
        TestAuth.signInAs(agent);

        editUser(reporter.getId(), "{\"displayName\": \"Hacked\"}")
                .andExpect(status().isForbidden());
        deactivate(reporter.getId(), false).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/{id}/reactivate", reporter.getId()))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(reporter.getId()).orElseThrow().isActive()).isTrue();
        assertThat(userRepository.findById(reporter.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Riley Reporter");
    }
}
