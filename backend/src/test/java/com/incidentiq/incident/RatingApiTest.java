package com.incidentiq.incident;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class RatingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    private UserAccount riley;
    private UserAccount olive;
    private UserAccount alex;
    private String incidentId;

    @BeforeEach
    void setUp() throws Exception {
        riley = userRepository.saveAndFlush(new UserAccount("rate.riley@incidentiq.test", "Riley Reporter", UserRole.USER));
        olive = userRepository.saveAndFlush(new UserAccount("rate.olive@incidentiq.test", "Olive Other", UserRole.USER));
        alex = userRepository.saveAndFlush(new UserAccount("rate.alex@incidentiq.test", "Alex Agent", UserRole.AGENT));

        TestAuth.signInAs(riley);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "VPN drops", "description": "Broken",
                                 "priority": "MEDIUM", "category": "Network"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        incidentId = JsonPath.read(body, "$.id");
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private void setStatus(String value) throws Exception {
        TestAuth.signInAs(alex);
        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions rate(UserAccount as, String json) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(put("/api/incidents/{id}/rating", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void theReporterRatesAResolvedIncident() throws Exception {
        rate(riley, "{\"score\": 5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can rate an incident once it's resolved."));

        setStatus("RESOLVED");
        rate(riley, "{\"score\": 4, \"comment\": \"  Quick fix, thanks  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satisfactionScore").value(4))
                .andExpect(jsonPath("$.satisfactionComment").value("Quick fix, thanks"))
                .andExpect(jsonPath("$.ratedAt").exists());

        // Changing your mind replaces the rating; both show on the timeline.
        rate(riley, "{\"score\": 2, \"comment\": \"\"}")
                .andExpect(jsonPath("$.satisfactionScore").value(2))
                .andExpect(jsonPath("$.satisfactionComment").doesNotExist());

        TestAuth.signInAs(alex);
        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$[0].type").value("SATISFACTION_RATED"))
                .andExpect(jsonPath("$[0].toValue").value("2"))
                .andExpect(jsonPath("$[0].actorName").value("Riley Reporter"))
                .andExpect(jsonPath("$[1].toValue").value("4"))
                .andExpect(jsonPath("$[1].message").value("Quick fix, thanks"));
    }

    @Test
    void closedIncidentsCanBeRatedToo() throws Exception {
        setStatus("CLOSED");
        rate(riley, "{\"score\": 3}").andExpect(status().isOk());
    }

    @Test
    void onlyTheReporterCanRate() throws Exception {
        setStatus("RESOLVED");

        rate(olive, "{\"score\": 1}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the person who reported an incident can rate it."));
        rate(alex, "{\"score\": 5}").andExpect(status().isForbidden());

        TestAuth.signInAs(alex);
        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(jsonPath("$.satisfactionScore").doesNotExist());
    }

    @Test
    void scoresMustBeOneToFive() throws Exception {
        setStatus("RESOLVED");

        rate(riley, "{\"score\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.score").value("Choose a rating from 1 to 5"));
        rate(riley, "{\"score\": 6}").andExpect(status().isBadRequest());
        rate(riley, "{}").andExpect(status().isBadRequest());
        rate(riley, "{\"score\": 3, \"comment\": \"" + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());
    }
}
