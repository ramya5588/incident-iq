package com.incidentiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * "Forgot password?" links. The emails themselves are sent after commit (see
 * EmailNotificationTest); here each test reads the link from the recorded
 * email event.
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
class PasswordResetApiTest {

    private static final Pattern LINK =
            Pattern.compile("https://iq\\.example/reset-password#token=([A-Za-z0-9_-]{43})");

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
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.parse("2026-01-05T09:00:00Z"));
        riley = save("riley@iq.example", "Riley Reporter", UserRole.USER);
        riley.setPasswordHash(passwordEncoder.encode("old-password-1"));
        userRepository.saveAndFlush(riley);
        admin = save("ada@iq.example", "Ada Admin", UserRole.ADMIN);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private ResultActions requestLink(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}"));
    }

    private ResultActions check(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\", \"newPassword\": \"" + password + "\"}"));
    }

    private List<AccountEmail> emails() {
        return events.stream(AccountEmail.class).toList();
    }

    private static String tokenIn(AccountEmail email) {
        Matcher matcher = LINK.matcher(email.text());
        assertThat(matcher.find()).as("reset link in: " + email.text()).isTrue();
        return matcher.group(1);
    }

    private boolean hasPassword(String password) {
        String hash = userRepository.findById(riley.getId()).orElseThrow().getPasswordHash();
        return passwordEncoder.matches(password, hash);
    }

    @Test
    void forgottenPasswordCanBeResetWithTheEmailedLink() throws Exception {
        mockMvc.perform(get("/api/auth/password-reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        requestLink("  RILEY@iq.example ")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(PasswordResetController.SENT_MESSAGE));

        assertThat(emails()).hasSize(1);
        AccountEmail email = emails().getFirst();
        assertThat(email.to()).isEqualTo("riley@iq.example");
        assertThat(email.subject()).isEqualTo("Reset your IncidentIQ password");
        assertThat(email.text()).contains("Hi Riley Reporter,", "expires in 30 minutes");
        String token = tokenIn(email);

        check(token).andExpect(status().isNoContent());
        confirm(token, "brand-new-password").andExpect(status().isNoContent());

        assertThat(hasPassword("brand-new-password")).isTrue();
        assertThat(emails()).hasSize(2);
        assertThat(emails().get(1).subject()).isEqualTo("Your IncidentIQ password was changed");

        // A link works once.
        check(token).andExpect(status().isBadRequest());
        confirm(token, "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("This reset link is invalid or has expired. Ask for a new one."));
        assertThat(hasPassword("brand-new-password")).isTrue();
    }

    @Test
    void unknownAndDeactivatedAccountsGetTheSameAnswerButNoEmail() throws Exception {
        requestLink("nobody@iq.example")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(PasswordResetController.SENT_MESSAGE));

        riley.deactivate(clock.instant());
        userRepository.saveAndFlush(riley);
        requestLink("riley@iq.example").andExpect(status().isAccepted());

        assertThat(emails()).isEmpty();
    }

    @Test
    void linksExpireAfterThirtyMinutes() throws Exception {
        requestLink("riley@iq.example").andExpect(status().isAccepted());
        String token = tokenIn(emails().getFirst());

        clock.advance(Duration.ofMinutes(29));
        check(token).andExpect(status().isNoContent());

        clock.advance(Duration.ofMinutes(1));
        check(token).andExpect(status().isBadRequest());
        confirm(token, "brand-new-password").andExpect(status().isBadRequest());
        assertThat(hasPassword("old-password-1")).isTrue();
    }

    @Test
    void aNewLinkCancelsTheOldOneAndRequestsAreLimited() throws Exception {
        for (int i = 0; i < PasswordResetService.MAX_REQUESTS_PER_HOUR + 1; i++) {
            requestLink("riley@iq.example").andExpect(status().isAccepted());
        }

        // The extra request in the same hour sends nothing.
        assertThat(emails()).hasSize(PasswordResetService.MAX_REQUESTS_PER_HOUR);
        String first = tokenIn(emails().getFirst());
        String latest = tokenIn(emails().getLast());
        check(first).andExpect(status().isBadRequest());
        check(latest).andExpect(status().isNoContent());

        clock.advance(Duration.ofMinutes(61));
        requestLink("riley@iq.example").andExpect(status().isAccepted());
        assertThat(emails()).hasSize(PasswordResetService.MAX_REQUESTS_PER_HOUR + 1);
    }

    @Test
    void deactivatingSomeoneMakesTheirLinkUseless() throws Exception {
        requestLink("riley@iq.example").andExpect(status().isAccepted());
        String token = tokenIn(emails().getFirst());

        riley.deactivate(clock.instant());
        userRepository.saveAndFlush(riley);

        confirm(token, "brand-new-password").andExpect(status().isBadRequest());
    }

    @Test
    void badInputIsRejected() throws Exception {
        requestLink("not-an-email").andExpect(status().isBadRequest());
        check("garbage").andExpect(status().isBadRequest());
        check("").andExpect(status().isBadRequest());

        requestLink("riley@iq.example").andExpect(status().isAccepted());
        String token = tokenIn(emails().getFirst());
        confirm(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword")
                        .value("New password must be 8 to 72 characters"));
        // Still usable after a rejected attempt.
        check(token).andExpect(status().isNoContent());
    }

    @Test
    void adminsCanEmailAMemberAResetLink() throws Exception {
        UserAccount agent = save("alex@iq.example", "Alex Agent", UserRole.AGENT);

        TestAuth.signInAs(agent);
        mockMvc.perform(post("/api/users/{id}/password-reset-link", riley.getId()))
                .andExpect(status().isForbidden());

        TestAuth.signInAs(admin);
        mockMvc.perform(post("/api/users/{id}/password-reset-link", riley.getId()))
                .andExpect(status().isAccepted());

        AccountEmail email = emails().getFirst();
        assertThat(email.to()).isEqualTo("riley@iq.example");
        assertThat(email.text()).contains("Ada Admin sent you a link");
        TestAuth.signOut();
        confirm(tokenIn(email), "riley-chose-this").andExpect(status().isNoContent());
        assertThat(hasPassword("riley-chose-this")).isTrue();

        // Not for deactivated members.
        TestAuth.signInAs(admin);
        riley.deactivate(clock.instant());
        userRepository.saveAndFlush(riley);
        mockMvc.perform(post("/api/users/{id}/password-reset-link", riley.getId()))
                .andExpect(status().isConflict());
    }
}
