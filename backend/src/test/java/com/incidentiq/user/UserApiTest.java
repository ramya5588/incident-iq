package com.incidentiq.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    private void createUser(String email, String name, String role) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "%s", "role": "%s"}
                                """.formatted(email, name, role)))
                .andExpect(status().isCreated());
    }

    @Test
    void listsUsersSortedByDisplayName() throws Exception {
        createUser("zoe@incidentiq.test", "Zoe Zhang", "AGENT");
        createUser("adam@incidentiq.test", "Adam Ames", "ADMIN");
        createUser("mia@incidentiq.test", "Mia Moore", "USER");

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].displayName").value("Adam Ames"))
                .andExpect(jsonPath("$[1].displayName").value("Mia Moore"))
                .andExpect(jsonPath("$[2].displayName").value("Zoe Zhang"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void normalizesEmailAndRejectsDuplicates() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Pat@IncidentIQ.test", "displayName": "Pat"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pat@incidentiq.test"))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "pat@incidentiq.test", "displayName": "Pat Again"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "displayName": "Nobody"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
