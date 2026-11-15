package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

/**
 * Not @Transactional: each incident is changed in its own transaction, as in
 * the app, so the data is committed and removed again after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
class BulkUpdateApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UserAccount riley;
    private UserAccount alex;
    private UserAccount sam;

    @BeforeEach
    void setUp() {
        riley = save("bulk.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        alex = save("bulk.alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        sam = save("bulk.sam@incidentiq.test", "Sam Agent", UserRole.AGENT);
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

    private String incident(String title) throws Exception {
        TestAuth.signInAs(riley);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "description": "Broken",
                                 "priority": "LOW", "category": "Network"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions bulk(UserAccount as, List<String> ids, String changes) throws Exception {
        TestAuth.signInAs(as);
        String idsJson = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return mockMvc.perform(patch("/api/incidents/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"incidentIds\": [" + idsJson + "]" + (changes.isEmpty() ? "" : ", " + changes) + "}"));
    }

    private ResultActions incidentAs(UserAccount as, String id) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(get("/api/incidents/{id}", id));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void changesSeveralIncidentsLikeSingleChangesWould() throws Exception {
        List<String> ids = List.of(incident("VPN drops"), incident("Printer jams"), incident("Wi-Fi slow"));

        bulk(alex, ids, """
                "status": "IN_PROGRESS", "priority": "HIGH", "assigneeId": "%s"
                """.formatted(sam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3))
                .andExpect(jsonPath("$.failed").isEmpty());

        for (String id : ids) {
            incidentAs(alex, id)
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.priority").value("HIGH"))
                    .andExpect(jsonPath("$.assignedToName").value("Sam Agent"))
                    // Starting work is the first response, as for a single change.
                    .andExpect(jsonPath("$.sla.response.state").value("MET"));
        }
        // One timeline entry per change, by Alex.
        assertThat(count("""
                SELECT count(*) FROM incident_events
                 WHERE actor_id = ? AND type IN ('STATUS_CHANGED', 'PRIORITY_CHANGED', 'ASSIGNEE_CHANGED')""",
                alex.getId())).isEqualTo(9);
        assertThat(count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = 'ASSIGNED'",
                sam.getId())).isEqualTo(3);
    }

    @Test
    void oneIncidentThatCantChangeDoesntStopTheOthers() throws Exception {
        String first = incident("VPN drops");
        String second = incident("Printer jams");
        String unknown = UUID.randomUUID().toString();

        bulk(alex, List.of(first, unknown, second, first), "\"status\": \"RESOLVED\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.failed.length()").value(1))
                .andExpect(jsonPath("$.failed[0].incidentId").value(unknown));

        incidentAs(alex, second).andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void eachIncidentChangesFullyOrNotAtAll() throws Exception {
        String id = incident("VPN drops");

        // Riley can't be assigned: the priority change is undone too.
        bulk(alex, List.of(id), """
                "priority": "CRITICAL", "assigneeId": "%s"
                """.formatted(riley.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failed[0].message").value(
                        "Riley Reporter can't be assigned incidents: only agents and admins can."));

        incidentAs(alex, id).andExpect(jsonPath("$.priority").value("LOW"));
        assertThat(count("SELECT count(*) FROM incident_events WHERE incident_id = ?::uuid AND type = 'PRIORITY_CHANGED'", id))
                .isZero();
    }

    @Test
    void putsOnHoldAndUnassigns() throws Exception {
        List<String> ids = List.of(incident("VPN drops"), incident("Printer jams"));
        bulk(alex, ids, "\"assigneeId\": \"" + alex.getId() + "\"").andExpect(jsonPath("$.updated").value(2));

        bulk(alex, ids, """
                "status": "ON_HOLD", "holdReason": "WAITING_ON_THIRD_PARTY", "holdNote": "Vendor ticket 42",
                "unassign": true
                """)
                .andExpect(jsonPath("$.updated").value(2));

        incidentAs(alex, ids.get(1))
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.holdNote").value("Vendor ticket 42"))
                .andExpect(jsonPath("$.assignedToId").doesNotExist());
    }

    @Test
    void refusesUnclearRequests() throws Exception {
        String id = incident("VPN drops");

        bulk(alex, List.of(), "\"status\": \"RESOLVED\"").andExpect(status().isBadRequest());
        bulk(alex, Collections.nCopies(101, id), "\"status\": \"RESOLVED\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.incidentIds").value("Change at most 100 incidents at a time"));
        bulk(alex, List.of(id), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a status, priority or assignee to change."));
        bulk(alex, List.of(id), "\"status\": \"ON_HOLD\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose why the incidents are on hold."));
        bulk(alex, List.of(id), "\"assigneeId\": \"" + sam.getId() + "\", \"unassign\": true")
                .andExpect(status().isBadRequest());

        incidentAs(alex, id).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void onlyAgentsAndAdminsCanBulkChange() throws Exception {
        String id = incident("VPN drops");

        bulk(riley, List.of(id), "\"status\": \"CLOSED\"").andExpect(status().isForbidden());
        incidentAs(riley, id).andExpect(jsonPath("$.status").value("OPEN"));
    }
}
