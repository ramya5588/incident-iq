package com.incidentiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/** Real sign-in through the API (no TestAuth shortcut), with sessions. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class AuthApiTest {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount alex;

    @BeforeEach
    void createAccount() {
        TestAuth.signOut();
        alex = new UserAccount("alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        alex.setPasswordHash(passwordEncoder.encode(PASSWORD));
        alex = userRepository.saveAndFlush(alex);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andReturn();
    }

    @Test
    void signInStartsASessionThatIdentifiesTheUser() throws Exception {
        MvcResult result = login("  ALEX@incidentiq.test ", PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"displayName\":\"Alex Agent\"")
                .doesNotContain("password");

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alex.getId().toString()))
                .andExpect(jsonPath("$.role").value("AGENT"));

        mockMvc.perform(get("/api/incidents").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        MvcResult wrongPassword = login("alex@incidentiq.test", "wrong-password");
        MvcResult unknownEmail = login("nobody@incidentiq.test", PASSWORD);

        for (MvcResult result : new MvcResult[] {wrongPassword, unknownEmail}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentAsString())
                    .contains("Incorrect email or password.");
            assertThat(result.getRequest().getSession(false)).isNull();
        }
    }

    @Test
    void accountWithoutPasswordCannotSignIn() throws Exception {
        userRepository.saveAndFlush(new UserAccount(
                "legacy@incidentiq.test", "Legacy User", UserRole.USER));

        assertThat(login("legacy@incidentiq.test", "").getResponse().getStatus())
                .isEqualTo(400);
        assertThat(login("legacy@incidentiq.test", "anything").getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void meIsUnauthorizedWhenSignedOut() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Not signed in."));
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        MockHttpSession session = (MockHttpSession) login("alex@incidentiq.test", PASSWORD)
                .getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWritesWithoutTheCsrfToken() throws Exception {
        // A MockMvc without the test-wide CSRF shortcut, like a real browser.
        // (The XSRF-TOKEN cookie itself can't be asserted here: once any test
        // uses Spring Security's csrf() helper it swaps the filter's token
        // store for a session-based test one for the rest of the run.)
        MockMvc browser = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        browser.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alex@incidentiq.test", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden());

        browser.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alex@incidentiq.test", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());
    }
}
