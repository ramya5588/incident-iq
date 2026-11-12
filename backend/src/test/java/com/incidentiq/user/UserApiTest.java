package com.incidentiq.user;

import static org.assertj.core.api.Assertions.assertThat;
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

    private UserAccount admin;

    @BeforeEach
    void signInAsAdmin() {
        admin = userRepository.saveAndFlush(new UserAccount(
                "admin@incidentiq.test", "Test Admin", UserRole.ADMIN));
        TestAuth.signInAs(admin);
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

    // --- roles and password resets -----------------------------------------

    private org.springframework.test.web.servlet.ResultActions changeRole(
            Object userId, String role) throws Exception {

        return mockMvc.perform(patch("/api/users/{id}/role", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": " + (role == null ? "null" : "\"" + role + "\"") + "}"));
    }

    @Test
    void adminCanChangeAMembersRole() throws Exception {
        UserAccount member = userRepository.saveAndFlush(new UserAccount(
                "member@incidentiq.test", "Member", UserRole.USER));

        changeRole(member.getId(), "AGENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));

        assertThat(userRepository.findById(member.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.AGENT);

        changeRole(member.getId(), null).andExpect(status().isBadRequest());
        changeRole(member.getId(), "OWNER").andExpect(status().isBadRequest());
        changeRole(java.util.UUID.randomUUID(), "AGENT").andExpect(status().isNotFound());
    }

    @Test
    void theLastAdminCannotBeDemoted() throws Exception {
        changeRole(admin.getId(), "AGENT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "There must always be at least one admin. Make someone else an admin first."));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);

        // With a second admin, demoting yourself is allowed.
        userRepository.saveAndFlush(new UserAccount(
                "second@incidentiq.test", "Second Admin", UserRole.ADMIN));
        changeRole(admin.getId(), "AGENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void demotionTakesEffectImmediately() throws Exception {
        userRepository.saveAndFlush(new UserAccount(
                "second@incidentiq.test", "Second Admin", UserRole.ADMIN));

        // The signed-in principal still says ADMIN, but the database now doesn't.
        changeRole(admin.getId(), "AGENT").andExpect(status().isOk());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "late@incidentiq.test", "displayName": "Too Late",
                                 "password": "test-password"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsCanChangeRolesOrResetPasswords() throws Exception {
        UserAccount agent = userRepository.saveAndFlush(new UserAccount(
                "agent@incidentiq.test", "Agent", UserRole.AGENT));
        TestAuth.signInAs(agent);

        changeRole(agent.getId(), "ADMIN").andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}/password", admin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"take-over-123\"}"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(agent.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.AGENT);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getPasswordHash())
                .isNull();
    }

    @Test
    void resetPasswordValidatesTheNewPassword() throws Exception {
        UserAccount member = userRepository.saveAndFlush(new UserAccount(
                "member@incidentiq.test", "Member", UserRole.USER));

        mockMvc.perform(put("/api/users/{id}/password", member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());

        mockMvc.perform(put("/api/users/{id}/password", member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"long-enough-1\"}"))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("long-enough-1",
                userRepository.findById(member.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
    }
}
