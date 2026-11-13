package com.incidentiq.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

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
 * Who gets notified, privacy of the notification endpoints, and SLA breach
 * announcements driven by a controllable clock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@Transactional
class NotificationApiTest {

    private static final Instant T0 = Instant.parse("2026-01-05T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private SlaBreachNotifier breachNotifier;

    private UserAccount reporter;
    private UserAccount alex;
    private UserAccount sam;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        clock.setInstant(T0);
        reporter = save("riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        alex = save("alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        sam = save("sam@incidentiq.test", "Sam Agent", UserRole.AGENT);
        admin = save("ada@incidentiq.test", "Ada Admin", UserRole.ADMIN);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    // --- helpers -----------------------------------------------------------

    private String createIncident(UserAccount as, String priority) throws Exception {
        TestAuth.signInAs(as);
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

    private void assign(UserAccount as, String incidentId, UserAccount assignee) throws Exception {
        TestAuth.signInAs(as);
        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + assignee.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private void comment(UserAccount as, String incidentId) throws Exception {
        TestAuth.signInAs(as);
        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Update\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions notificationsOf(UserAccount user) throws Exception {
        TestAuth.signInAs(user);
        return mockMvc.perform(get("/api/notifications"));
    }

    private long unreadOf(UserAccount user) throws Exception {
        TestAuth.signInAs(user);
        String body = mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.unread")).longValue();
    }

    // --- triggers ----------------------------------------------------------

    @Test
    void assigningNotifiesTheAssigneeOnly() throws Exception {
        String id = createIncident(reporter, "HIGH");

        assign(alex, id, sam);

        notificationsOf(sam)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].incidentId").value(id))
                .andExpect(jsonPath("$[0].actorName").value("Alex Agent"))
                .andExpect(jsonPath("$[0].readAt").doesNotExist())
                .andExpect(jsonPath("$[0].message").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "Alex Agent assigned you INC-\\d{3,} “VPN drops”")));
        assertThat(unreadOf(alex)).isZero();
        assertThat(unreadOf(reporter)).isZero();

        // Taking an incident yourself doesn't notify you.
        String other = createIncident(reporter, "LOW");
        assign(alex, other, alex);
        assertThat(unreadOf(alex)).isZero();
    }

    @Test
    void commentsNotifyReporterAndAssigneeButNeverTheAuthor() throws Exception {
        String id = createIncident(reporter, "MEDIUM");
        assign(alex, id, sam);
        TestAuth.signInAs(sam);
        mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isOk());

        comment(reporter, id);
        assertThat(unreadOf(sam)).isEqualTo(1);
        assertThat(unreadOf(reporter)).isZero();

        comment(sam, id);
        assertThat(unreadOf(reporter)).isEqualTo(1);
        assertThat(unreadOf(sam)).isEqualTo(1);

        comment(admin, id);
        assertThat(unreadOf(reporter)).isEqualTo(2);
        assertThat(unreadOf(sam)).isEqualTo(2);
        assertThat(unreadOf(admin)).isZero();

        notificationsOf(reporter)
                .andExpect(jsonPath("$[0].type").value("COMMENTED"))
                .andExpect(jsonPath("$[0].actorName").value("Ada Admin"));
    }

    @Test
    void statusChangesNotifyTheReporter() throws Exception {
        String id = createIncident(reporter, "HIGH");

        TestAuth.signInAs(alex);
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk());

        notificationsOf(reporter)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].message").value(
                        org.hamcrest.Matchers.endsWith("“VPN drops” to Resolved")));
        assertThat(unreadOf(alex)).isZero();
    }

    // --- reading -----------------------------------------------------------

    @Test
    void notificationsArePrivateAndCanBeMarkedRead() throws Exception {
        String id = createIncident(reporter, "HIGH");
        assign(alex, id, sam);
        comment(reporter, id);

        String body = notificationsOf(sam)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("COMMENTED"))
                .andExpect(jsonPath("$[1].type").value("ASSIGNED"))
                .andReturn().getResponse().getContentAsString();
        Number samsFirst = JsonPath.read(body, "$[1].id");

        // Someone else's notification looks like a missing one.
        TestAuth.signInAs(alex);
        mockMvc.perform(post("/api/notifications/{id}/read", samsFirst))
                .andExpect(status().isNotFound());
        notificationsOf(alex).andExpect(jsonPath("$.length()").value(0));
        assertThat(unreadOf(sam)).isEqualTo(2);

        TestAuth.signInAs(sam);
        mockMvc.perform(post("/api/notifications/{id}/read", samsFirst))
                .andExpect(status().isNoContent());
        // Marking again is harmless.
        mockMvc.perform(post("/api/notifications/{id}/read", samsFirst))
                .andExpect(status().isNoContent());
        assertThat(unreadOf(sam)).isEqualTo(1);
        notificationsOf(sam).andExpect(jsonPath("$[1].readAt").isNotEmpty());

        TestAuth.signInAs(sam);
        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(1));
        assertThat(unreadOf(sam)).isZero();

        TestAuth.signInAs(sam);
        mockMvc.perform(post("/api/notifications/{id}/read", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedOutCannotReadNotifications() throws Exception {
        TestAuth.signOut();
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    // --- SLA breaches ------------------------------------------------------

    @Test
    void breachesAreAnnouncedOnceToAdminsOrTheAssignee() throws Exception {
        String id = createIncident(reporter, "HIGH"); // respond 1h, resolve 8h

        clock.advance(Duration.ofMinutes(59));
        assertThat(breachNotifier.announceBreaches()).isZero();

        clock.advance(Duration.ofMinutes(2));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(1);
        assertThat(breachNotifier.announceBreaches()).isZero();

        // Unassigned: every admin hears about it; agents don't.
        notificationsOf(admin)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SLA_BREACHED"))
                .andExpect(jsonPath("$[0].actorName").doesNotExist())
                .andExpect(jsonPath("$[0].message").value(
                        org.hamcrest.Matchers.endsWith("missed its response deadline")));
        assertThat(unreadOf(sam)).isZero();

        // Once assigned, the assignee is told about the resolution deadline.
        assign(admin, id, sam);
        TestAuth.signInAs(sam);
        mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isOk());

        clock.advance(Duration.ofHours(7));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(1);

        notificationsOf(sam)
                .andExpect(jsonPath("$[0].type").value("SLA_BREACHED"))
                .andExpect(jsonPath("$[0].message").value(
                        org.hamcrest.Matchers.endsWith("missed its resolution deadline")));
        assertThat(unreadOf(admin)).isEqualTo(1);
    }

    @Test
    void resolvedIncidentsAreNeverAnnounced() throws Exception {
        String id = createIncident(reporter, "CRITICAL");
        TestAuth.signInAs(alex);
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk());

        clock.advance(Duration.ofDays(2));
        assertThat(breachNotifier.announceBreaches()).isZero();
    }

    @Test
    void movingADeadlineLaterLetsItBeAnnouncedAgain() throws Exception {
        String id = createIncident(reporter, "CRITICAL"); // respond 15m

        clock.advance(Duration.ofMinutes(20));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(1);

        // LOW: respond within 8h of being reported, so not breached any more.
        TestAuth.signInAs(admin);
        mockMvc.perform(patch("/api/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"LOW\"}"))
                .andExpect(status().isOk());
        assertThat(breachNotifier.announceBreaches()).isZero();

        clock.advance(Duration.ofHours(8));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(1);
    }
}
