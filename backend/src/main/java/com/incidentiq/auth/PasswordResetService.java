package com.incidentiq.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.incident.UserNotFoundException;
import com.incidentiq.notification.AccountEmail;
import com.incidentiq.notification.EmailSettings;
import com.incidentiq.user.TeamRuleException;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

/**
 * "Forgot password?": emails a one-time link that expires, then sets the new
 * password. Links are random 256-bit tokens; only their SHA-256 hash is
 * stored. A new link cancels older ones, and using one cancels the rest.
 */
@Service
public class PasswordResetService {

    /** Self-service requests allowed per account per hour (admins aren't limited). */
    static final int MAX_REQUESTS_PER_HOUR = 3;

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokens;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailSettings emailSettings;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration linkLifetime;

    public PasswordResetService(
            PasswordResetTokenRepository tokens,
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            EmailSettings emailSettings,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${incidentiq.auth.password-reset-link-lifetime:30m}") Duration linkLifetime) {

        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailSettings = emailSettings;
        this.events = events;
        this.clock = clock;
        this.linkLifetime = linkLifetime;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Reset links need email. */
    public boolean available() {
        return emailSettings.enabled();
    }

    /**
     * Emails a link if the address belongs to an active account. Says nothing
     * either way, so it can't be used to find out who has an account.
     */
    @Transactional
    public void requestReset(String email) {
        requireEmail();
        users.findByEmailIgnoreCase(email.trim())
                .filter(UserAccount::isActive)
                .ifPresent(user -> {
                    Instant hourAgo = now().minus(Duration.ofHours(1));
                    if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), hourAgo)
                            >= MAX_REQUESTS_PER_HOUR) {
                        log.info("Password reset for user {} skipped: too many requests", user.getId());
                        return;
                    }
                    sendLink(user, null);
                });
    }

    /** An admin sends a member a link, so they choose their own password. */
    @Transactional
    public void sendLinkFromAdmin(UUID userId, UUID adminId) {
        requireEmail();
        UserAccount user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!user.isActive()) {
            throw new TeamRuleException(user.getDisplayName()
                    + " is deactivated. Reactivate them before sending a reset link.");
        }
        UserAccount admin = users.findById(adminId).orElseThrow(() -> new UserNotFoundException(adminId));
        sendLink(user, admin);
        log.info("Admin {} sent a password reset link to user {}", adminId, userId);
    }

    /** Throws {@link InvalidResetLinkException} unless the link can still be used. */
    @Transactional(readOnly = true)
    public void check(String token) {
        findUsable(token);
    }

    /**
     * Sets the new password and uses up the link. Other sessions of the
     * account are signed out on their next request (the password changed).
     */
    @Transactional
    public void reset(String token, String newPassword) {
        UUID userId = findUsable(token).getUser().getId();
        Instant now = now();

        if (tokens.use(OneTimeTokens.hash(token.trim()), now) == 0) {
            throw new InvalidResetLinkException();
        }
        tokens.cancelAll(userId, now);
        UserAccount user = users.findById(userId).orElseThrow(InvalidResetLinkException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.saveAndFlush(user);

        events.publishEvent(new AccountEmail(user.getEmail(),
                "Your IncidentIQ password was changed",
                """
                Hi %s,

                Your IncidentIQ password was just changed using a reset link. \
                Any other devices signed in to your account have been signed out.

                If this wasn't you, contact your IncidentIQ admin right away.
                """.formatted(user.getDisplayName())));
        log.info("User {} reset their password with an emailed link", user.getId());
    }

    private void sendLink(UserAccount user, UserAccount admin) {
        Instant now = now();
        tokens.cancelAll(user.getId(), now);

        String token = OneTimeTokens.newToken();
        tokens.save(new PasswordResetToken(user, OneTimeTokens.hash(token), now, now.plus(linkLifetime)));

        String why = admin == null
                ? "Someone (hopefully you) asked to reset the password for your IncidentIQ account."
                : admin.getDisplayName() + " sent you a link to choose a new IncidentIQ password.";
        events.publishEvent(new AccountEmail(user.getEmail(),
                "Reset your IncidentIQ password",
                """
                Hi %s,

                %s

                Choose a new password here: %s

                The link works once and expires in %d minutes. If you didn't \
                ask for this, ignore this email: your password stays the same.
                """.formatted(user.getDisplayName(), why,
                        emailSettings.passwordResetLink(token), linkLifetime.toMinutes())));
    }

    private PasswordResetToken findUsable(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidResetLinkException();
        }
        Instant now = now();
        return tokens.findByTokenHash(OneTimeTokens.hash(token.trim()))
                .filter(link -> link.usableAt(now))
                .orElseThrow(InvalidResetLinkException::new);
    }

    private void requireEmail() {
        if (!available()) {
            throw new PasswordResetUnavailableException();
        }
    }
}
