package com.incidentiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.incidentiq.MutableClock;
import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/**
 * Sign-in lockout. Commits its data (a wrong current password is counted in
 * its own transaction) and deletes it afterwards. Uses the same setup as
 * EmailNotificationTest so the lockout email can be checked. Each test signs
 * in from its own address, so the per-address limit doesn't carry over.
 */
@SpringBootTest(properties = {
        "spring.mail.host=smtp.test",
        "incidentiq.mail.from=IncidentIQ <incidents@iq.example>",
        "incidentiq.mail.app-url=https://iq.example/",
        "incidentiq.sla.breach-check-initial-delay=1h"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
class SignInProtectionTest {

    private static final String PASSWORD = "correct-horse-1";

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> created = new ArrayList<>();
    private UserAccount riley;
    private UserAccount admin;
    private String address;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.parse("2026-04-01T09:00:00Z"));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        riley = save("lock.riley." + suffix + "@incidentiq.test", "Riley Reporter", UserRole.USER);
        admin = save("lock.ada." + suffix + "@incidentiq.test", "Ada Admin", UserRole.ADMIN);
        // A different made-up address for every test.
        address = "10.%d.%d.%d".formatted((int) (Math.random() * 200) + 1,
                (int) (Math.random() * 250), (int) (Math.random() * 250));
        reset(mailSender);
    }

    @AfterEach
    void cleanUp() {
        TestAuth.signOut();
        for (UUID id : created) {
            jdbc.update("DELETE FROM app_users WHERE id = ?", id);
        }
    }

    private UserAccount save(String email, String name, UserRole role) {
        UserAccount user = new UserAccount(email, name, role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.saveAndFlush(user);
        created.add(user.getId());
        return user;
    }

    private ResultActions signIn(String email, String password) throws Exception {
        return signIn(email, password, address);
    }

    private ResultActions signIn(String email, String password, String from) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(from);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private void failTimes(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            signIn(email, "wrong-" + i).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        }
    }

    @Test
    void locksAnAccountAfterFiveWrongPasswords() throws Exception {
        failTimes(riley.getEmail(), 4);

        signIn(riley.getEmail(), "wrong-5")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "Too many failed sign-ins. Try again in 15 minutes, or reset your password with \"Forgot password?\"."));
        // Even the right password is refused while locked.
        signIn(riley.getEmail(), PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(userRepository.findById(riley.getId()).orElseThrow().getLockedUntil())
                .isEqualTo(Instant.parse("2026-04-01T09:15:00Z"));

        clock.advance(Duration.ofMinutes(14));
        signIn(riley.getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", Matchers.startsWith("Too many failed sign-ins. Try again in 1 minute,")));

        // It clears itself, and a successful sign-in starts the count again.
        clock.advance(Duration.ofMinutes(2));
        signIn(riley.getEmail(), PASSWORD).andExpect(status().isOk());
        failTimes(riley.getEmail(), 4);
        signIn(riley.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void emailsTheOwnerWhenTheAccountLocks() throws Exception {
        failTimes(riley.getEmail(), 4);
        signIn(riley.getEmail(), "wrong-5").andExpect(status().isTooManyRequests());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(riley.getEmail());
        assertThat(sent.getValue().getSubject()).isEqualTo("Someone tried to sign in to your IncidentIQ account");
        assertThat(sent.getValue().getText())
                .contains("5 failed attempts")
                .contains("https://iq.example/forgot-password");
    }

    @Test
    void failuresSpreadOutDontLock() throws Exception {
        failTimes(riley.getEmail(), 4);
        clock.advance(Duration.ofMinutes(16));
        failTimes(riley.getEmail(), 4);

        signIn(riley.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailsLookExactlyLikeRealAccounts() throws Exception {
        String nobody = "nobody." + UUID.randomUUID() + "@incidentiq.test";
        failTimes(nobody, 4);

        signIn(nobody, "wrong-5")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", Matchers.startsWith("Too many failed sign-ins. Try again in 15 minutes")));
    }

    @Test
    void limitsFailuresFromOneAddressAcrossAccounts() throws Exception {
        for (int i = 0; i < SignInGuard.MAX_FAILURES_PER_ADDRESS - 1; i++) {
            signIn("guess" + i + "." + UUID.randomUUID() + "@incidentiq.test", "password")
                    .andExpect(status().isUnauthorized());
        }
        // The 20th failure is the one that trips the limit.
        signIn("guess-last." + UUID.randomUUID() + "@incidentiq.test", "password")
                .andExpect(status().isTooManyRequests());

        signIn(riley.getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "Too many failed sign-ins from your network. Try again in a few minutes."));
        // Someone else, elsewhere, isn't affected.
        signIn(riley.getEmail(), PASSWORD, "192.0.2.77").andExpect(status().isOk());
    }

    @Test
    void adminsSeeLocksAndCanUnlock() throws Exception {
        failTimes(riley.getEmail(), 4);
        signIn(riley.getEmail(), "wrong-5").andExpect(status().isTooManyRequests());

        TestAuth.signInAs(admin);
        mockMvc.perform(get("/api/users"))
                .andExpect(jsonPath("$[?(@.id == '%s')].lockedUntil".formatted(riley.getId()),
                        Matchers.contains("2026-04-01T09:15:00Z")));

        TestAuth.signInAs(riley);
        mockMvc.perform(post("/api/users/{id}/unlock", riley.getId())).andExpect(status().isForbidden());

        TestAuth.signInAs(admin);
        mockMvc.perform(post("/api/users/{id}/unlock", riley.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
        TestAuth.signOut();

        signIn(riley.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anAdminPasswordResetAlsoUnlocks() throws Exception {
        failTimes(riley.getEmail(), 4);
        signIn(riley.getEmail(), "wrong-5").andExpect(status().isTooManyRequests());

        TestAuth.signInAs(admin);
        mockMvc.perform(put("/api/users/{id}/password", riley.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"brand-new-pass-2\"}"))
                .andExpect(status().is2xxSuccessful());
        TestAuth.signOut();

        signIn(riley.getEmail(), "brand-new-pass-2").andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordsWhileSignedInCountToo() throws Exception {
        TestAuth.signInAs(riley);
        for (int i = 0; i < SignInGuard.MAX_FAILURES; i++) {
            mockMvc.perform(put("/api/auth/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\": \"guess-%d\", \"newPassword\": \"another-pass-3\"}".formatted(i)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"another-pass-3\"}".formatted(PASSWORD)))
                .andExpect(status().isTooManyRequests());
        TestAuth.signOut();
        signIn(riley.getEmail(), PASSWORD).andExpect(status().isTooManyRequests());
    }
}
