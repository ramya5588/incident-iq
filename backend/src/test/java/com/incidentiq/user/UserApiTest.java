package com.incidentiq.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void signInAsAdmin() {
        TestAuth.signInAs(userRepository.saveAndFlush(new UserAccount(
                "admin@incidentiq.test", "Test Admin", UserRole.ADMIN)));
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private void createUser(String email, String name, String role) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "%s", "role": "%s",
                                 "password": "test-password"}
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
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].displayName").value("Adam Ames"))
                .andExpect(jsonPath("$[1].displayName").value("Mia Moore"))
                .andExpect(jsonPath("$[2].displayName").value("Test Admin"))
                .andExpect(jsonPath("$[3].displayName").value("Zoe Zhang"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void normalizesEmailAndRejectsDuplicates() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "  Pat@IncidentIQ.test ", "displayName": " Pat ",
                                 "password": "test-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pat@incidentiq.test"))
                .andExpect(jsonPath("$.displayName").value("Pat"))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "pat@incidentiq.test", "displayName": "Pat Again",
                                 "password": "test-password"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankDisplayName() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "blank@incidentiq.test", "displayName": "   ",
                                 "password": "test-password"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "displayName": "Nobody",
                                 "password": "test-password"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAPasswordOfAtLeastEightCharacters() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nopass@incidentiq.test", "displayName": "No Pass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").value("Password is required"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "short@incidentiq.test", "displayName": "Short",
                                 "password": "1234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void storesOnlyAHashAndNeverReturnsIt() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "hash@incidentiq.test", "displayName": "Hash",
                                 "password": "correct-horse"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = userRepository.findByEmailIgnoreCase("hash@incidentiq.test")
                .orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", hash)).isTrue();
    }

    @Test
    void onlyAdminsCanAddTeamMembersButEveryoneCanListThem() throws Exception {
        for (UserRole role : new UserRole[] {UserRole.AGENT, UserRole.USER}) {
            TestAuth.signInAs(userRepository.saveAndFlush(new UserAccount(
                    role.name().toLowerCase() + "@incidentiq.test", "Not Admin", role)));

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "sneaky@incidentiq.test", "displayName": "Sneaky",
                                     "role": "ADMIN", "password": "test-password"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("You don't have permission to do that."));

            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isOk());
        }

        assertThat(userRepository.findByEmailIgnoreCase("sneaky@incidentiq.test")).isEmpty();
    }
}
