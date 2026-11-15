package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import com.incidentiq.notification.NotificationType;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class MentionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserAccount riley;
    private UserAccount otherReporter;
    private UserAccount alex;
    private UserAccount sam;
    private UserAccount admin;
    private String incidentId;

    @BeforeEach
    void setUp() throws Exception {
        riley = save("men.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        otherReporter = save("men.olive@incidentiq.test", "Olive Other", UserRole.USER);
        alex = save("men.alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        sam = save("men.sam@incidentiq.test", "Sam Agent", UserRole.AGENT);
        admin = save("men.ada@incidentiq.test", "Ada Admin", UserRole.ADMIN);

        TestAuth.signInAs(riley);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "VPN drops", "description": "Every hour",
                                 "priority": "MEDIUM", "category": "Network"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        incidentId = JsonPath.read(body, "$.id");

        TestAuth.signInAs(alex);
        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + alex.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private static String mention(String name, UUID id) {
        return "@[" + name + "](" + id + ")";
    }

    private ResultActions comment(UserAccount as, String body, boolean internal) throws Exception {
        TestAuth.signInAs(as);
        // Bodies here have no quotes or backslashes to escape.
        String json = "{\"body\": \"" + body + "\", \"internal\": " + internal + "}";
        return mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private List<Notification> notificationsFor(UserAccount user) {
        return notificationRepository
                .findByRecipientIdOrderByIdDesc(user.getId(), PageRequest.of(0, 50));
    }

    private List<String> mentionsFor(UserAccount user) {
        return notificationsFor(user).stream()
                .filter(n -> n.getType() == NotificationType.MENTIONED)
                .map(Notification::getMessage)
                .toList();
    }

    @Test
    void mentionsUseTheRealNameAndNotifyThePersonOnce() throws Exception {
        int alexBefore = notificationsFor(alex).size();

        // The client's name for Sam is ignored; the stored mention uses his real one.
        comment(admin, "Thanks " + mention("The CEO", sam.getId()) + " and "
                + mention("Alex Agent", alex.getId()) + ", also " + mention("x", sam.getId()), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Thanks " + mention("Sam Agent", sam.getId())
                        + " and " + mention("Alex Agent", alex.getId())
                        + ", also " + mention("Sam Agent", sam.getId())));

        assertThat(mentionsFor(sam)).singleElement().asString()
                .matches("Ada Admin mentioned you on INC-\\d+ “VPN drops”");
        // Alex is the assignee: one notification (the mention), not a "commented" as well.
        assertThat(notificationsFor(alex)).hasSize(alexBefore + 1);
        assertThat(mentionsFor(alex)).hasSize(1);
        // Riley wasn't mentioned, so gets the usual "commented".
        assertThat(notificationsFor(riley)).extracting(Notification::getMessage)
                .anySatisfy(m -> assertThat(m).startsWith("Ada Admin commented on"));
    }

    @Test
    void internalNotesOnlyMentionTheTeam() throws Exception {
        comment(sam, mention("Riley Reporter", riley.getId()) + " can't see this; "
                + mention("Ada Admin", admin.getId()) + " can", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("@Riley Reporter can't see this; "
                        + mention("Ada Admin", admin.getId()) + " can"));

        assertThat(mentionsFor(admin)).singleElement().asString()
                .startsWith("Sam Agent mentioned you in an internal note on INC-");
        assertThat(notificationsFor(riley)).noneMatch(n -> n.getMessage().contains("Sam"));
    }

    @Test
    void reportersCanMentionTheTeamButNotOtherReporters() throws Exception {
        UserAccount gone = save("men.gone@incidentiq.test", "Gone Agent", UserRole.AGENT);
        gone.deactivate(Instant.now());
        userRepository.saveAndFlush(gone);

        comment(riley, mention("Sam Agent", sam.getId()) + " "
                + mention("Olive Other", otherReporter.getId()) + " "
                + mention("Gone Agent", gone.getId()) + " "
                + mention("Nobody", UUID.randomUUID()), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(mention("Sam Agent", sam.getId())
                        + " @Olive Other @Gone Agent @Nobody"));

        assertThat(mentionsFor(sam)).hasSize(1);
        assertThat(notificationsFor(otherReporter)).isEmpty();
        assertThat(notificationsFor(gone)).isEmpty();

        // Agents can mention the incident's own reporter in a reply.
        comment(alex, mention("Riley Reporter", riley.getId()) + " can you retry?", false)
                .andExpect(jsonPath("$.message").value(
                        mention("Riley Reporter", riley.getId()) + " can you retry?"));
        assertThat(mentionsFor(riley)).singleElement().asString()
                .startsWith("Alex Agent mentioned you on INC-");
    }

    @Test
    void theLengthLimitCountsMentionsAsTheyRead() throws Exception {
        // 2000 characters as read, though the stored text is longer.
        String sam = mention("Sam Agent", this.sam.getId());
        String fits = sam + " " + "x".repeat(2000 - "@Sam Agent ".length());
        comment(alex, fits, false).andExpect(status().isCreated());

        comment(alex, fits + "x", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Comment must not exceed 2000 characters"));
        comment(alex, "x".repeat(2001), false).andExpect(status().isBadRequest());
    }

    @Test
    void mentioningYourselfNotifiesNobody() throws Exception {
        comment(alex, "Note to " + mention("Alex Agent", alex.getId()), false)
                .andExpect(status().isCreated());
        assertThat(mentionsFor(alex)).isEmpty();

        TestAuth.signInAs(riley);
        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$[0].message").value("Note to " + mention("Alex Agent", alex.getId())));
    }
}
