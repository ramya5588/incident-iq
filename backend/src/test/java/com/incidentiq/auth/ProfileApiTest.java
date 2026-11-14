package com.incidentiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.MutableClock;
import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.notification.AccountEmail;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/**
 * Members changing their own name and email, with email set up (so a new
 * email waits for confirmation). Same setup as PasswordResetApiTest, so the
 * two share a test context.
 */
@SpringBootTest(properties = {
        "spring.mail.host=smtp.test",
        "incidentiq.mail.from=IncidentIQ <incidents@iq.example>",
        "incidentiq.mail.app-url=https://iq.example"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class, MutableClock.Config.class})
@RecordApplicationEvents
@Transactional
class ProfileApiTest {

    private static final String PASSWORD = "riley-password-1";
    private static final Pattern LINK =
            Pattern.compile("https://iq\\.example/confirm-email#token=([A-Za-z0-9_-]{43})");

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount riley;
    private UserAccount alex;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.parse("2026-01-05T09:00:00Z"));
        riley = new UserAccount("riley@iq.example", "Riley Reporter", UserRole.USER);
        riley.setPasswordHash(passwordEncoder.encode(PASSWORD));
        riley = userRepository.saveAndFlush(riley);
        alex = userRepository.saveAndFlush(
                new UserAccount("alex@iq.example", "Alex Agent", UserRole.AGENT));
        TestAuth.signInAs(riley);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private ResultActions requestChange(String newEmail, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/email-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\": \"" + newEmail + "\", \"currentPassword\": \"" + password + "\"}"));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/email-change/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"));
    }

    private List<AccountEmail> emails() {
        return events.stream(AccountEmail.class).toList();
    }

    private static String tokenIn(AccountEmail email) {
        Matcher matcher = LINK.matcher(email.text());
        assertThat(matcher.find()).as("confirmation link in: " + email.text()).isTrue();
        return matcher.group(1);
    }

    private String rileysEmail() {
        return userRepository.findById(riley.getId()).orElseThrow().getEmail();
    }

    @Test
    void membersCanRenameThemselves() throws Exception {
        mockMvc.perform(patch("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"  Riley R. \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Riley R."))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(patch("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.displayName").value("Display name must not be blank"));

        // A role can't be slipped in: it's not part of the request.
        mockMvc.perform(patch("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Riley\", \"role\": \"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Alex Agent");
    }

    @Test
    void aNewEmailAppliesOnlyOnceConfirmedFromThatAddress() throws Exception {
        requestChange("  Riley.New@IQ.example ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("riley@iq.example"))
                .andExpect(jsonPath("$.pending.newEmail").value("riley.new@iq.example"))
                .andExpect(jsonPath("$.pending.expiresAt").value("2026-01-06T09:00:00Z"));

        assertThat(rileysEmail()).isEqualTo("riley@iq.example");
        mockMvc.perform(get("/api/auth/email-change"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newEmail").value("riley.new@iq.example"));

        AccountEmail confirmEmail = emails().getFirst();
        assertThat(confirmEmail.to()).isEqualTo("riley.new@iq.example");
        assertThat(confirmEmail.subject()).isEqualTo("Confirm your new IncidentIQ email");
        assertThat(confirmEmail.text()).contains("instead of riley@iq.example", "expires in 24 hours");

        // The link works signed out too.
        TestAuth.signOut();
        String token = tokenIn(confirmEmail);
        confirm(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("riley.new@iq.example"));

        assertThat(rileysEmail()).isEqualTo("riley.new@iq.example");
        AccountEmail notice = emails().get(1);
        assertThat(notice.to()).isEqualTo("riley@iq.example");
        assertThat(notice.subject()).isEqualTo("Your IncidentIQ email was changed");
        assertThat(notice.text()).contains("from riley@iq.example to riley.new@iq.example");

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "This confirmation link is invalid or has expired. Ask for the change again."));

        TestAuth.signInAs(riley);
        mockMvc.perform(get("/api/auth/email-change")).andExpect(status().isNoContent());
    }

    @Test
    void theCurrentPasswordIsRequiredAndEmailsStayUnique() throws Exception {
        requestChange("riley.new@iq.example", "wrong-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your current password is incorrect."));
        requestChange("RILEY@iq.example", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That's already your email."));
        requestChange("ALEX@iq.example", PASSWORD).andExpect(status().isConflict());
        requestChange("not-an-email", PASSWORD).andExpect(status().isBadRequest());

        assertThat(emails()).isEmpty();
    }

    @Test
    void anAddressTakenBeforeConfirmingCantBeUsed() throws Exception {
        requestChange("shared@iq.example", PASSWORD).andExpect(status().isOk());
        String token = tokenIn(emails().getFirst());

        userRepository.saveAndFlush(new UserAccount("shared@iq.example", "Sam Someone", UserRole.USER));

        confirm(token).andExpect(status().isConflict());
        assertThat(rileysEmail()).isEqualTo("riley@iq.example");
    }

    @Test
    void linksExpireAndCanBeCancelledOrReplaced() throws Exception {
        requestChange("first@iq.example", PASSWORD).andExpect(status().isOk());
        String first = tokenIn(emails().getFirst());
        requestChange("second@iq.example", PASSWORD).andExpect(status().isOk());
        String second = tokenIn(emails().get(1));

        // Only the latest request counts.
        confirm(first).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/email-change"))
                .andExpect(jsonPath("$.newEmail").value("second@iq.example"));

        mockMvc.perform(delete("/api/auth/email-change")).andExpect(status().isNoContent());
        confirm(second).andExpect(status().isBadRequest());

        requestChange("third@iq.example", PASSWORD).andExpect(status().isOk());
        String third = tokenIn(emails().get(2));
        clock.advance(Duration.ofHours(24));
        confirm(third).andExpect(status().isBadRequest());
        assertThat(rileysEmail()).isEqualTo("riley@iq.example");

        // Three requests in the hour (the clock moved, so it's a new hour).
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < ProfileService.MAX_REQUESTS_PER_HOUR; i++) {
            requestChange("more" + i + "@iq.example", PASSWORD).andExpect(status().isOk());
        }
        requestChange("one-too-many@iq.example", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void deactivatingSomeoneVoidsTheirLink() throws Exception {
        requestChange("riley.new@iq.example", PASSWORD).andExpect(status().isOk());
        String token = tokenIn(emails().getFirst());

        UserAccount current = userRepository.findById(riley.getId()).orElseThrow();
        current.deactivate(clock.instant());
        userRepository.saveAndFlush(current);

        TestAuth.signOut();
        confirm(token).andExpect(status().isBadRequest());
    }

    @Test
    void youMustBeSignedInToChangeThings() throws Exception {
        TestAuth.signOut();
        mockMvc.perform(patch("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Nobody\"}"))
                .andExpect(status().isUnauthorized());
        requestChange("x@iq.example", PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/email-change")).andExpect(status().isUnauthorized());
    }
}
