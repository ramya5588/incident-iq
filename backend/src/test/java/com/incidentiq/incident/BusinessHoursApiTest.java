package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.hamcrest.Matchers;
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
import com.incidentiq.notification.SlaBreachNotifier;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

/**
 * SLA business hours through the API. Targets (application.properties):
 * HIGH respond 1h / resolve 8h, CRITICAL 15m / 4h. January 2026: Friday the
 * 9th, Monday the 12th. London is on GMT then, so local time = UTC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@Transactional
class BusinessHoursApiTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private SlaBreachNotifier breachNotifier;

    private UserAccount admin;
    private UserAccount agent;

    @BeforeEach
    void setUp() {
        admin = userRepository.saveAndFlush(
                new UserAccount("bh.admin@incidentiq.test", "Bea Admin", UserRole.ADMIN));
        agent = userRepository.saveAndFlush(
                new UserAccount("bh.agent@incidentiq.test", "Ben Agent", UserRole.AGENT));
        TestAuth.signInAs(agent);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private static Instant london(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(LONDON).toInstant();
    }

    private ResultActions saveSettings(String json) throws Exception {
        TestAuth.signInAs(admin);
        ResultActions result = mockMvc.perform(put("/api/sla/business-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
        TestAuth.signInAs(agent);
        return result;
    }

    /** Mon–Fri 09:00–17:00 London for HIGH, with optional holidays JSON. */
    private void enableBusinessHours(String holidaysJson) throws Exception {
        saveSettings("""
                {"enabled": true, "timeZone": "Europe/London", "dayStart": "09:00", "dayEnd": "17:00",
                 "workingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
                 "priorities": ["HIGH"], "holidays": %s}
                """.formatted(holidaysJson)).andExpect(status().isOk());
    }

    private String createIncident(String priority) throws Exception {
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Printer offline", "description": "Floor 2",
                                 "priority": "%s", "category": "Hardware"}
                                """.formatted(priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions getIncident(String id) throws Exception {
        return mockMvc.perform(get("/api/incidents/{id}", id));
    }

    private void setStatus(String id, String json) throws Exception {
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void offByDefaultWithSensibleSuggestions() throws Exception {
        mockMvc.perform(get("/api/sla/business-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.dayStart").value("09:00"))
                .andExpect(jsonPath("$.dayEnd").value("17:00"))
                .andExpect(jsonPath("$.workingDays", Matchers.contains(
                        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")))
                .andExpect(jsonPath("$.priorities", Matchers.contains("LOW", "MEDIUM", "HIGH")))
                .andExpect(jsonPath("$.holidays").isEmpty());

        // Off: deadlines run around the clock, as before.
        clock.setInstant(london("2026-01-09T16:30"));
        getIncident(createIncident("HIGH"))
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-09T17:30:00Z"))
                .andExpect(jsonPath("$.sla.response.businessHours").value(false))
                .andExpect(jsonPath("$.sla.response.countedSecondsLeft").value(3600));
    }

    @Test
    void deadlinesCountOnlyBusinessHoursForTheChosenPriorities() throws Exception {
        enableBusinessHours("[{\"date\": \"2026-01-12\", \"name\": \"Team day off\"}]");
        clock.setInstant(london("2026-01-09T16:30"));

        String high = createIncident("HIGH");
        String critical = createIncident("CRITICAL");

        // 30 minutes on Friday, Monday is a holiday, the rest on Tuesday.
        getIncident(high)
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-13T09:30:00Z"))
                .andExpect(jsonPath("$.sla.response.businessHours").value(true))
                .andExpect(jsonPath("$.sla.response.countedSecondsLeft").value(3600))
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-13T16:30:00Z"));
        // CRITICAL isn't chosen: still around the clock.
        getIncident(critical)
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-09T16:45:00Z"))
                .andExpect(jsonPath("$.sla.response.businessHours").value(false));

        // Saturday: Friday's 30 minutes are used, the weekend doesn't count, so
        // HIGH has 30 working minutes left and isn't overdue.
        clock.setInstant(london("2026-01-10T12:00"));
        getIncident(high)
                .andExpect(jsonPath("$.sla.response.state").value("ON_TRACK"))
                .andExpect(jsonPath("$.sla.response.countedSecondsLeft").value(1800));
        mockMvc.perform(get("/api/incidents").param("overdue", "true"))
                .andExpect(jsonPath("$.content[*].id", Matchers.contains(critical)));
        assertThat(breachNotifier.announceBreaches()).isEqualTo(2);

        // Tuesday 09:20: 10 working minutes left of the hour, so at risk.
        clock.setInstant(london("2026-01-13T09:20"));
        getIncident(high)
                .andExpect(jsonPath("$.sla.response.state").value("AT_RISK"))
                .andExpect(jsonPath("$.sla.response.countedSecondsLeft").value(600));
        clock.setInstant(london("2026-01-13T09:30"));
        getIncident(high).andExpect(jsonPath("$.sla.response.state").value("BREACHED"));
    }

    @Test
    void aWeekendOnHoldAddsOnlyTheWorkingTimeHeld() throws Exception {
        enableBusinessHours("[]");
        clock.setInstant(london("2026-01-09T16:00"));
        String id = createIncident("HIGH");
        // Resolve 8h: 1h on Friday + 7h on Monday.
        getIncident(id).andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-12T16:00:00Z"));

        clock.setInstant(london("2026-01-09T16:45"));
        setStatus(id, "{\"status\": \"ON_HOLD\", \"holdReason\": \"WAITING_ON_THIRD_PARTY\"}");

        clock.setInstant(london("2026-01-10T12:00"));
        getIncident(id)
                .andExpect(jsonPath("$.sla.resolution.state").value("PAUSED"))
                // 15m Friday + 7h Monday still to go.
                .andExpect(jsonPath("$.sla.resolution.countedSecondsLeft").value(26100));

        // Back Monday 10:00: held for 15m Friday + 1h Monday = 75 working minutes.
        clock.setInstant(london("2026-01-12T10:00"));
        setStatus(id, "{\"status\": \"IN_PROGRESS\"}");
        getIncident(id)
                .andExpect(jsonPath("$.sla.resolution.dueAt").value("2026-01-13T09:15:00Z"))
                .andExpect(jsonPath("$.sla.resolution.state").value("ON_TRACK"));
    }

    @Test
    void changesApplyToNewDeadlinesOnly() throws Exception {
        enableBusinessHours("[]");
        clock.setInstant(london("2026-01-09T16:30"));
        String id = createIncident("HIGH");

        saveSettings("""
                {"enabled": false, "timeZone": "Europe/London", "dayStart": "09:00", "dayEnd": "17:00",
                 "workingDays": ["MONDAY"], "priorities": ["HIGH"], "holidays": []}
                """).andExpect(status().isOk());

        // The existing deadline stays; a new incident runs around the clock.
        getIncident(id).andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-12T09:30:00Z"));
        getIncident(createIncident("HIGH"))
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-09T17:30:00Z"));

        // A priority change recalculates with the current (now off) settings.
        mockMvc.perform(patch("/api/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"MEDIUM\"}"))
                .andExpect(jsonPath("$.sla.response.dueAt").value("2026-01-09T20:30:00Z"));
    }

    @Test
    void onlyAdminsChangeTheSettingsAndTheyAreChecked() throws Exception {
        String valid = """
                {"enabled": true, "timeZone": "America/New_York", "dayStart": "08:30", "dayEnd": "18:00",
                 "workingDays": ["SUNDAY", "MONDAY"], "priorities": [],
                 "holidays": [{"date": "2026-12-25", "name": " Christmas "}]}
                """;
        mockMvc.perform(put("/api/sla/business-hours")
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isForbidden());

        saveSettings(valid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.dayStart").value("08:30"))
                .andExpect(jsonPath("$.workingDays", Matchers.contains("MONDAY", "SUNDAY")))
                .andExpect(jsonPath("$.priorities").isEmpty())
                .andExpect(jsonPath("$.holidays[0].name").value("Christmas"));

        saveSettings(valid.replace("America/New_York", "Mars/Olympus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown time zone: Mars/Olympus"));
        saveSettings(valid.replace("\"08:30\"", "\"19:00\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The working day must start before it ends."));
        saveSettings(valid.replace("\"08:30\"", "\"8.30am\"")).andExpect(status().isBadRequest());
        saveSettings(valid.replace("[\"SUNDAY\", \"MONDAY\"]", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose at least one working day."));
        saveSettings(valid.replace("[{\"date\": \"2026-12-25\", \"name\": \" Christmas \"}]",
                        "[{\"date\": \"2026-12-25\", \"name\": \"A\"}, {\"date\": \"2026-12-25\", \"name\": \"B\"}]"))
                .andExpect(status().isBadRequest());

        // Failed saves left the good ones in place.
        mockMvc.perform(get("/api/sla/business-hours"))
                .andExpect(jsonPath("$.timeZone").value("America/New_York"));
    }
}
