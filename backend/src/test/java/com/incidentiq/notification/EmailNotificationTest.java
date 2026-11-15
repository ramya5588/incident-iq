package com.incidentiq.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.incidentiq.MutableClock;
import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

/**
 * Notification emails, with a fake mail server. Emails go out only after a
 * change is committed, so unlike most API tests this one commits its data
 * (and deletes it afterwards) instead of rolling back.
 */
@SpringBootTest(properties = {
        "spring.mail.host=smtp.test",
        "incidentiq.mail.from=IncidentIQ <incidents@iq.example>",
        "incidentiq.mail.app-url=https://iq.example/",
        // Keep the scheduled breach check out of the way; tests run it directly.
        "incidentiq.sla.breach-check-initial-delay=1h"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
class EmailNotificationTest {

    private static final Instant T0 = Instant.parse("2026-01-05T09:00:00Z");
    private static final Duration WAIT = Duration.ofSeconds(5);

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SlaBreachNotifier breachNotifier;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UserAccount reporter;
    private UserAccount agent;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        clock.setInstant(T0);
        reporter = save("riley@mail.test", "Riley Reporter", UserRole.USER);
        agent = save("alex@mail.test", "Alex Agent", UserRole.AGENT);
        admin = save("ada@mail.test", "Ada Admin", UserRole.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        TestAuth.signOut();
        for (UUID id : users) {
            jdbc.update("""
                    DELETE FROM notifications WHERE recipient_id = ?
                       OR incident_id IN (SELECT id FROM incidents WHERE created_by = ?)""", id, id);
            jdbc.update("""
                    DELETE FROM incident_events
                     WHERE incident_id IN (SELECT id FROM incidents WHERE created_by = ?)""", id);
            jdbc.update("DELETE FROM incidents WHERE created_by = ?", id);
        }
        for (UUID id : users) {
            jdbc.update("DELETE FROM app_users WHERE id = ?", id);
        }
    }

    private UserAccount save(String email, String name, UserRole role) {
        UserAccount user = userRepository.saveAndFlush(new UserAccount(email, name, role));
        users.add(user.getId());
        return user;
    }

    private String createIncident(String priority) throws Exception {
        TestAuth.signInAs(reporter);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "VPN drops", "description": "Every hour",
                                 "priority": "%s", "category": "Network"}
                                """.formatted(priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions assignToAgent(String incidentId) throws Exception {
        TestAuth.signInAs(admin);
        return mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": \"" + agent.getId() + "\"}"));
    }

    private void commentAsReporter(String incidentId) throws Exception {
        TestAuth.signInAs(reporter);
        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Still broken\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions savePreferences(UserAccount as, String json) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(put("/api/notifications/email-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private SimpleMailMessage nextEmail() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(WAIT.toMillis())).send(sent.capture());
        return sent.getValue();
    }

    private void expectNoEmail() {
        verify(mailSender, after(700).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void assignmentIsEmailedToTheAssigneeWithALink() throws Exception {
        String id = createIncident("HIGH");
        assignToAgent(id).andExpect(status().isOk());

        SimpleMailMessage email = nextEmail();
        assertThat(email.getTo()).containsExactly("alex@mail.test");
        assertThat(email.getFrom()).isEqualTo("IncidentIQ <incidents@iq.example>");
        assertThat(email.getSubject()).matches("Ada Admin assigned you INC-\\d{3,} “VPN drops”");
        assertThat(email.getText())
                .startsWith("Hi Alex Agent,")
                .contains("Open the incident: https://iq.example/incidents/" + id)
                .contains("Account page: https://iq.example/account");
    }

    @Test
    void commentsAreEmailedOnlyToPeopleWhoTurnedThemOn() throws Exception {
        String id = createIncident("HIGH");
        assignToAgent(id).andExpect(status().isOk());
        nextEmail();
        reset(mailSender);

        // Off by default: in-app only.
        commentAsReporter(id);
        expectNoEmail();
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(agent.getId()))
                .isEqualTo(2);

        savePreferences(agent, """
                {"assigned": true, "commented": true, "statusChanged": false, "slaBreached": true}
                """).andExpect(status().isOk());
        commentAsReporter(id);

        SimpleMailMessage email = nextEmail();
        assertThat(email.getTo()).containsExactly("alex@mail.test");
        assertThat(email.getSubject()).startsWith("Riley Reporter commented on INC-");
    }

    @Test
    void mentionsAreEmailedByDefault() throws Exception {
        String id = createIncident("HIGH");
        reset(mailSender);

        // The reporter mentions Alex, who isn't assigned: emailed (on by default).
        TestAuth.signInAs(reporter);
        mockMvc.perform(post("/api/incidents/{id}/comments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"@[Alex](" + agent.getId() + ") help?\"}"))
                .andExpect(status().isCreated());

        SimpleMailMessage email = nextEmail();
        assertThat(email.getTo()).containsExactly("alex@mail.test");
        assertThat(email.getSubject()).startsWith("Riley Reporter mentioned you on INC-");
    }

    @Test
    void turningAnEmailOffStopsIt() throws Exception {
        savePreferences(agent, """
                {"assigned": false, "commented": false, "statusChanged": false, "slaBreached": true}
                """).andExpect(status().isOk());

        assignToAgent(createIncident("HIGH")).andExpect(status().isOk());

        expectNoEmail();
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(agent.getId()))
                .isEqualTo(1);
    }

    @Test
    void missedDeadlinesAreEmailed() throws Exception {
        String id = createIncident("HIGH");
        assignToAgent(id).andExpect(status().isOk());
        nextEmail();
        reset(mailSender);

        clock.advance(Duration.ofHours(9));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(1);

        SimpleMailMessage email = nextEmail();
        assertThat(email.getTo()).containsExactly("alex@mail.test");
        assertThat(email.getSubject()).endsWith("missed its resolution deadline");
    }

    @Test
    void watchersHearAboutMissedDeadlinesToo() throws Exception {
        String id = createIncident("HIGH");
        assignToAgent(id).andExpect(status().isOk());
        TestAuth.signInAs(reporter);
        mockMvc.perform(put("/api/incidents/{id}/watchers/me", id)).andExpect(status().isOk());

        clock.advance(Duration.ofHours(9));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(1);

        // Alex as the assignee, and Riley as a watcher.
        assertThat(notificationRepository.findByRecipientIdOrderByIdDesc(
                        reporter.getId(), org.springframework.data.domain.PageRequest.of(0, 5)))
                .anySatisfy(n -> assertThat(n.getMessage()).endsWith("missed its resolution deadline"));
    }

    @Test
    void aFailedSendDoesntUndoTheChange() throws Exception {
        doThrow(new MailSendException("Mail server down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        String id = createIncident("HIGH");
        assignToAgent(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToName").value("Alex Agent"));

        verify(mailSender, timeout(WAIT.toMillis())).send(any(SimpleMailMessage.class));
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(agent.getId()))
                .isEqualTo(1);
    }

    @Test
    void preferencesStartWithTheDefaultsAndCanBeChanged() throws Exception {
        TestAuth.signInAs(agent);
        mockMvc.perform(get("/api/notifications/email-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.commented").value(false))
                .andExpect(jsonPath("$.statusChanged").value(false))
                .andExpect(jsonPath("$.slaBreached").value(true))
                .andExpect(jsonPath("$.mentioned").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(true));

        savePreferences(agent, "{\"assigned\": true}").andExpect(status().isBadRequest());

        savePreferences(agent, """
                {"assigned": false, "commented": true, "statusChanged": true, "slaBreached": false}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false))
                .andExpect(jsonPath("$.statusChanged").value(true))
                // Left out of the request, so unchanged.
                .andExpect(jsonPath("$.mentioned").value(true));

        savePreferences(agent, """
                {"assigned": false, "commented": true, "statusChanged": true, "slaBreached": false,
                 "mentioned": false}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentioned").value(false));

        // Only your own preferences: Riley's are untouched.
        TestAuth.signInAs(reporter);
        mockMvc.perform(get("/api/notifications/email-preferences"))
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.statusChanged").value(false));

        TestAuth.signOut();
        mockMvc.perform(get("/api/notifications/email-preferences"))
                .andExpect(status().isUnauthorized());
    }
}
