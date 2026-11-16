package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
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
class MergeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserAccount riley;
    private UserAccount olive;
    private UserAccount sam;
    private UserAccount alex;
    private String main;
    private String duplicate;

    @BeforeEach
    void setUp() throws Exception {
        riley = save("merge.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        olive = save("merge.olive@incidentiq.test", "Olive Other", UserRole.USER);
        sam = save("merge.sam@incidentiq.test", "Sam Agent", UserRole.AGENT);
        alex = save("merge.alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        main = incident(riley, "Printer offline");
        duplicate = incident(olive, "Printer not working");
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private String incident(UserAccount reporter, String title) throws Exception {
        TestAuth.signInAs(reporter);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "description": "Broken",
                                 "priority": "MEDIUM", "category": "Hardware"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions markDuplicate(UserAccount as, String id, String mainId) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(put("/api/incidents/{id}/duplicate-of", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mainIncidentId\": \"" + mainId + "\"}"));
    }

    private ResultActions undo(UserAccount as, String id) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(delete("/api/incidents/{id}/duplicate-of", id));
    }

    private ResultActions getIncident(UserAccount as, String id) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(get("/api/incidents/{id}", id));
    }

    private ResultActions events(String id) throws Exception {
        TestAuth.signInAs(alex);
        return mockMvc.perform(get("/api/incidents/{id}/events", id));
    }

    @Test
    void closesTheDuplicateAndPointsAtTheMainIncident() throws Exception {
        TestAuth.signInAs(sam);
        mockMvc.perform(put("/api/incidents/{id}/watchers/me", duplicate)).andExpect(status().isOk());

        markDuplicate(alex, duplicate, main)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.duplicateOfId").value(main))
                .andExpect(jsonPath("$.duplicateOfTitle").value("Printer offline"));

        String mainNumber = JsonPath.read(getIncident(alex, main).andReturn().getResponse().getContentAsString(),
                "$.incidentNumber").toString();
        events(duplicate)
                .andExpect(jsonPath("$[0].type").value("MERGED_INTO"))
                .andExpect(jsonPath("$[0].toValue").value("INC-%03d".formatted(Long.parseLong(mainNumber))))
                .andExpect(jsonPath("$[1].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].message", Matchers.startsWith("Duplicate of INC-")));
        events(main).andExpect(jsonPath("$[0].type").value("DUPLICATE_MERGED"));

        TestAuth.signInAs(alex);
        mockMvc.perform(get("/api/incidents/{id}/duplicates", main))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(duplicate))
                .andExpect(jsonPath("$[0].title").value("Printer not working"));
    }

    @Test
    void theDuplicatesPeopleFollowTheMainIncident() throws Exception {
        TestAuth.signInAs(sam);
        mockMvc.perform(put("/api/incidents/{id}/watchers/me", duplicate));

        markDuplicate(alex, duplicate, main).andExpect(status().isOk());

        // Olive (who reported the duplicate) and Sam (who watched it) now watch the main one.
        TestAuth.signInAs(olive);
        mockMvc.perform(get("/api/incidents/{id}/watchers", main))
                .andExpect(jsonPath("$.watching").value(true))
                .andExpect(jsonPath("$.count").value(2));
        assertThat(notificationRepository.findByRecipientIdOrderByIdDesc(olive.getId(), PageRequest.of(0, 5)))
                .extracting(Notification::getMessage)
                .anySatisfy(m -> assertThat(m).contains("as a duplicate of").endsWith("Updates continue there."));

        // So the main incident's updates reach them.
        TestAuth.signInAs(alex);
        mockMvc.perform(post("/api/incidents/{id}/comments", main)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Fixed the print server\"}"))
                .andExpect(status().isCreated());
        assertThat(notificationRepository.findByRecipientIdOrderByIdDesc(olive.getId(), PageRequest.of(0, 5)))
                .extracting(Notification::getMessage)
                .anySatisfy(m -> assertThat(m).startsWith("Alex Agent commented on"));
    }

    @Test
    void undoingReopensTheDuplicate() throws Exception {
        markDuplicate(alex, duplicate, main).andExpect(status().isOk());

        undo(alex, duplicate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.duplicateOfId").doesNotExist());
        events(duplicate).andExpect(jsonPath("$[*].type", Matchers.hasItem("UNMERGED")));
        events(main).andExpect(jsonPath("$[0].type").value("DUPLICATE_UNMERGED"));
        TestAuth.signInAs(alex);
        mockMvc.perform(get("/api/incidents/{id}/duplicates", main)).andExpect(jsonPath("$.length()").value(0));

        undo(alex, duplicate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.endsWith("isn't marked as a duplicate.")));
    }

    @Test
    void refusesMergesThatWouldMakeAMess() throws Exception {
        String third = incident(riley, "Printer jams");

        markDuplicate(alex, main, main)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An incident can't be a duplicate of itself."));

        markDuplicate(alex, duplicate, main).andExpect(status().isOk());
        // Already a duplicate.
        markDuplicate(alex, duplicate, third)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.endsWith("Undo that first.")));
        // The main incident has duplicates, so it can't become one.
        markDuplicate(alex, main, third)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("has duplicates merged into it")));
        // A duplicate can't be the main incident.
        markDuplicate(alex, third, duplicate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.endsWith("Choose that one instead.")));

        markDuplicate(alex, third, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        TestAuth.signInAs(alex);
        mockMvc.perform(put("/api/incidents/{id}/duplicate-of", third)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        getIncident(alex, third).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void onlyAgentsAndAdminsCanMerge() throws Exception {
        markDuplicate(riley, duplicate, main).andExpect(status().isForbidden());
        undo(riley, duplicate).andExpect(status().isForbidden());
        getIncident(riley, duplicate).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void reportsCountADuplicateOnce() throws Exception {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        TestAuth.signInAs(alex);
        int before = JsonPath.read(mockMvc.perform(get("/api/reports/summary")
                        .param("from", today).param("to", today).param("timeZone", "UTC"))
                .andReturn().getResponse().getContentAsString(), "$.reported");

        markDuplicate(alex, duplicate, main).andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/summary").param("from", today).param("to", today).param("timeZone", "UTC"))
                .andExpect(jsonPath("$.reported").value(before - 1));
    }
}
