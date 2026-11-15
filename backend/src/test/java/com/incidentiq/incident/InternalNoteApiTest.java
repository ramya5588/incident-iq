package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.notification.Notification;
import com.incidentiq.notification.NotificationRepository;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class InternalNoteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserAccount riley;
    private UserAccount agent;
    private UserAccount otherAgent;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        riley = save("note.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        agent = save("note.agent@incidentiq.test", "Alex Agent", UserRole.AGENT);
        otherAgent = save("note.agent2@incidentiq.test", "Avery Agent", UserRole.AGENT);
        admin = save("note.admin@incidentiq.test", "Ada Admin", UserRole.ADMIN);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    /** Reported by {@code reporter} and assigned to Alex. */
    private String incident(UserAccount reporter) throws Exception {
        TestAuth.signInAs(reporter);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "VPN drops", "description": "Every hour",
                                 "priority": "MEDIUM", "category": "Network"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        TestAuth.signInAs(agent);
        mockMvc.perform(patch("/api/incidents/{id}/assignee", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + agent.getId() + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions comment(UserAccount as, String id, String body, Boolean internal)
            throws Exception {

        TestAuth.signInAs(as);
        String internalJson = internal == null ? "" : ", \"internal\": " + internal;
        return mockMvc.perform(post("/api/incidents/{id}/comments", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \"" + body + "\"" + internalJson + "}"));
    }

    private ResultActions events(UserAccount as, String id) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(get("/api/incidents/{id}/events", id));
    }

    private List<String> notificationsFor(UserAccount user) {
        return notificationRepository
                .findByRecipientIdOrderByIdDesc(user.getId(), PageRequest.of(0, 50))
                .stream()
                .map(Notification::getMessage)
                .toList();
    }

    @Test
    void reportersNeverReceiveInternalNotes() throws Exception {
        String id = incident(riley);
        comment(agent, id, "Looks like the VPN cert expired", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true))
                .andExpect(jsonPath("$.actorName").value("Alex Agent"));
        comment(agent, id, "We're looking into it", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(false));

        // The team sees both.
        for (UserAccount teamMember : List.of(agent, otherAgent, admin)) {
            events(teamMember, id)
                    .andExpect(jsonPath("$[?(@.type == 'COMMENT')].message", Matchers.containsInAnyOrder(
                            "Looks like the VPN cert expired", "We're looking into it")))
                    .andExpect(jsonPath("$[?(@.internal == true)].message",
                            Matchers.contains("Looks like the VPN cert expired")));
        }

        // The reporter gets only the reply; the note isn't in the response at all.
        String body = events(riley, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'COMMENT')].message",
                        Matchers.contains("We're looking into it")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("VPN cert");
    }

    @Test
    void onlyAgentsAndAdminsCanAddInternalNotes() throws Exception {
        String id = incident(riley);

        comment(riley, id, "Can I hide this?", true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only agents and admins can add internal notes."));
        comment(riley, id, "A normal reply", false).andExpect(status().isCreated());
        comment(admin, id, "Admin note", true).andExpect(status().isCreated());

        events(admin, id)
                .andExpect(jsonPath("$[?(@.type == 'COMMENT')].message",
                        Matchers.containsInAnyOrder("A normal reply", "Admin note")));
        comment(agent, id, " ", true).andExpect(status().isBadRequest());
    }

    @Test
    void internalNotesNotifyTheTeamButNeverTheReporter() throws Exception {
        String id = incident(riley);

        comment(otherAgent, id, "Seen this before", true).andExpect(status().isCreated());

        assertThat(notificationsFor(agent)).anySatisfy(message ->
                assertThat(message).matches("Avery Agent added an internal note on INC-\\d+ “VPN drops”"));
        assertThat(notificationsFor(riley)).noneMatch(message -> message.contains("note"));
        assertThat(notificationsFor(otherAgent)).isEmpty();

        // An agent who reported the incident is part of the team, so is told.
        String agentReported = incident(otherAgent);
        comment(agent, agentReported, "Checking the logs", true).andExpect(status().isCreated());
        assertThat(notificationsFor(otherAgent)).anySatisfy(message ->
                assertThat(message).startsWith("Alex Agent added an internal note"));
    }

    @Test
    void anInternalNoteIsNotTheReplyAHoldIsWaitingFor() throws Exception {
        // Reported by an agent, so the reporter could write a note.
        String id = incident(otherAgent);
        TestAuth.signInAs(agent);
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ON_HOLD\", \"holdReason\": \"WAITING_ON_REPORTER\"}"))
                .andExpect(status().isOk());

        comment(otherAgent, id, "Note to self: find the error", true).andExpect(status().isCreated());
        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(jsonPath("$.status").value("ON_HOLD"));

        comment(otherAgent, id, "Here's the error", null).andExpect(status().isCreated());
        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
}
