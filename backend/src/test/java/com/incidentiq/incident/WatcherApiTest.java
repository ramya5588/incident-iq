package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
class WatcherApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserAccount riley;
    private UserAccount olive;
    private UserAccount alex;
    private UserAccount sam;
    private UserAccount admin;
    private String incidentId;

    @BeforeEach
    void setUp() throws Exception {
        riley = save("watch.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        olive = save("watch.olive@incidentiq.test", "Olive Other", UserRole.USER);
        alex = save("watch.alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        sam = save("watch.sam@incidentiq.test", "Sam Agent", UserRole.AGENT);
        admin = save("watch.ada@incidentiq.test", "Ada Admin", UserRole.ADMIN);
        incidentId = incident("VPN drops");
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private String incident(String title) throws Exception {
        TestAuth.signInAs(riley);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "description": "Broken",
                                 "priority": "MEDIUM", "category": "Network"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions watch(UserAccount as, String id) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(put("/api/incidents/{id}/watchers/me", id));
    }

    private ResultActions watchers(UserAccount as) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(get("/api/incidents/{id}/watchers", incidentId));
    }

    private void comment(UserAccount as, String body, boolean internal) throws Exception {
        TestAuth.signInAs(as);
        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"" + body + "\", \"internal\": " + internal + "}"))
                .andExpect(status().isCreated());
    }

    private void setStatus(UserAccount as, String status) throws Exception {
        TestAuth.signInAs(as);
        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> messagesFor(UserAccount user) {
        return notificationRepository
                .findByRecipientIdOrderByIdDesc(user.getId(), PageRequest.of(0, 50))
                .stream()
                .map(Notification::getMessage)
                .toList();
    }

    @Test
    void anyoneCanWatchAndOnlyTheTeamSeesWho() throws Exception {
        watch(olive, incidentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watching").value(true))
                .andExpect(jsonPath("$.count").value(1))
                // Reporters see the count, not who.
                .andExpect(jsonPath("$.watchers").doesNotExist());
        watch(olive, incidentId).andExpect(jsonPath("$.count").value(1));
        watch(sam, incidentId).andExpect(jsonPath("$.watchers[*].displayName",
                Matchers.contains("Olive Other", "Sam Agent")));

        watchers(alex)
                .andExpect(jsonPath("$.watching").value(false))
                .andExpect(jsonPath("$.count").value(2));

        TestAuth.signInAs(olive);
        mockMvc.perform(delete("/api/incidents/{id}/watchers/me", incidentId))
                .andExpect(jsonPath("$.watching").value(false))
                .andExpect(jsonPath("$.count").value(1));

        watch(olive, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        TestAuth.signOut();
        mockMvc.perform(get("/api/incidents/{id}/watchers", incidentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void watchersHearAboutCommentsAndStatusChanges() throws Exception {
        watch(olive, incidentId);
        watch(sam, incidentId);

        comment(alex, "Looking into it", false);
        setStatus(alex, "IN_PROGRESS");

        assertThat(messagesFor(olive)).anySatisfy(m -> assertThat(m).startsWith("Alex Agent commented on"))
                .anySatisfy(m -> assertThat(m).startsWith("Alex Agent changed INC-")
                        .endsWith("to In progress"));
        assertThat(messagesFor(sam)).hasSize(2);
        // Nobody is told about their own action.
        watch(alex, incidentId);
        comment(alex, "More detail", false);
        assertThat(messagesFor(alex)).isEmpty();
    }

    @Test
    void reportersWatchingNeverHearAboutInternalNotes() throws Exception {
        watch(olive, incidentId);
        watch(sam, incidentId);

        comment(alex, "Cert expired", true);

        assertThat(messagesFor(olive)).isEmpty();
        assertThat(messagesFor(sam)).singleElement().asString()
                .startsWith("Alex Agent added an internal note");
    }

    @Test
    void onlyOneNotificationEvenIfAlsoReporterOrAssignee() throws Exception {
        TestAuth.signInAs(alex);
        mockMvc.perform(patch("/api/incidents/{id}/assignee", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + alex.getId() + "\"}"))
                .andExpect(status().isOk());
        watch(alex, incidentId);
        watch(riley, incidentId);
        int alexBefore = messagesFor(alex).size();

        comment(admin, "Any news?", false);

        assertThat(messagesFor(alex)).hasSize(alexBefore + 1);
        assertThat(messagesFor(riley)).filteredOn(m -> m.contains("commented")).hasSize(1);
    }

    @Test
    void deactivatedWatchersAreNotNotified() throws Exception {
        watch(sam, incidentId);
        sam.deactivate(Instant.now());
        userRepository.saveAndFlush(sam);

        comment(alex, "Update", false);

        assertThat(messagesFor(sam)).isEmpty();
    }

    @Test
    void mentionedPeopleStartWatching() throws Exception {
        comment(admin, "@[Sam](" + sam.getId() + ") and @[me](" + admin.getId() + ") look", false);

        watchers(sam).andExpect(jsonPath("$.watching").value(true));
        // Mentioning yourself doesn't make you a watcher.
        watchers(admin).andExpect(jsonPath("$.watching").value(false))
                .andExpect(jsonPath("$.count").value(1));

        // Sam now hears about the replies.
        comment(alex, "Reply", false);
        assertThat(messagesFor(sam)).anySatisfy(m -> assertThat(m).startsWith("Alex Agent commented on"));
    }

    @Test
    void theListCanShowOnlyWatchedIncidents() throws Exception {
        String other = incident("Printer jams");
        watch(olive, other);

        TestAuth.signInAs(olive);
        mockMvc.perform(get("/api/incidents").param("watching", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(other));
        mockMvc.perform(get("/api/incidents").param("watching", "true").param("status", "CLOSED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
