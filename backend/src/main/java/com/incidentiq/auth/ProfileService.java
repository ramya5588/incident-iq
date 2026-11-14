package com.incidentiq.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.notification.AccountEmail;
import com.incidentiq.notification.EmailSettings;
import com.incidentiq.user.DuplicateEmailException;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserResponse;

/**
 * Members changing their own name and sign-in email. A new email needs the
 * current password and, when email is set up, a click on a link sent to the
 * new address before it applies (so a typo can't lock anyone out). The old
 * address is told when it changes.
 */
@Service
public class ProfileService {

    /** Email change requests allowed per account per hour. */
    static final int MAX_REQUESTS_PER_HOUR = 3;

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserAccountRepository users;
    private final EmailChangeRequestRepository requests;
    private final PasswordEncoder passwordEncoder;
    private final EmailSettings emailSettings;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration linkLifetime;

    public ProfileService(
            UserAccountRepository users,
            EmailChangeRequestRepository requests,
            PasswordEncoder passwordEncoder,
            EmailSettings emailSettings,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${incidentiq.auth.email-change-link-lifetime:24h}") Duration linkLifetime) {

        this.users = users;
        this.requests = requests;
        this.passwordEncoder = passwordEncoder;
        this.emailSettings = emailSettings;
        this.events = events;
        this.clock = clock;
        this.linkLifetime = linkLifetime;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private UserAccount findUser(UUID userId) {
        // The signed-in user; the session filter has already checked they exist.
        return users.findById(userId).orElseThrow();
    }

    @Transactional
    public UserResponse rename(UUID userId, String displayName) {
        UserAccount user = findUser(userId);
        user.setDisplayName(displayName);
        log.info("User {} changed their display name", userId);
        return UserResponse.from(users.saveAndFlush(user));
    }

    /**
     * Starts (or, without email set up, makes) a change of sign-in email.
     * A new request replaces any open one.
     */
    @Transactional
    public EmailChangeResult requestEmailChange(UUID userId, String newEmail, String currentPassword) {
        UserAccount user = findUser(userId);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AccountRuleException("Your current password is incorrect.");
        }

        String email = newEmail.trim().toLowerCase(Locale.ROOT);
        if (email.equals(user.getEmail())) {
            throw new AccountRuleException("That's already your email.");
        }
        requireUnused(email, userId);

        Instant now = now();
        if (!emailSettings.enabled()) {
            // Nothing to confirm with: the password check is the confirmation.
            requests.cancelAll(userId, now);
            user = findUser(userId);
            user.setEmail(email);
            log.info("User {} changed their sign-in email (no email confirmation available)", userId);
            return new EmailChangeResult(UserResponse.from(users.saveAndFlush(user)), null);
        }

        if (requests.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1)))
                >= MAX_REQUESTS_PER_HOUR) {
            throw new TooManyRequestsException(
                    "You've asked to change your email several times in the last hour. Try again later.");
        }

        requests.cancelAll(userId, now);
        user = findUser(userId);
        String token = OneTimeTokens.newToken();
        EmailChangeRequest request = requests.save(new EmailChangeRequest(
                user, email, OneTimeTokens.hash(token), now, now.plus(linkLifetime)));

        events.publishEvent(new AccountEmail(email,
                "Confirm your new IncidentIQ email",
                """
                Hi %s,

                You asked to sign in to IncidentIQ with this address instead of %s.

                Confirm it here: %s

                The link works once and expires in %d hours. Until you confirm, \
                you keep signing in with your current email. If you didn't ask \
                for this, ignore this email.
                """.formatted(user.getDisplayName(), user.getEmail(),
                        emailSettings.emailChangeLink(token), linkLifetime.toHours())));
        log.info("User {} asked to change their sign-in email", userId);

        return new EmailChangeResult(UserResponse.from(user), PendingEmailChange.of(request));
    }

    @Transactional(readOnly = true)
    public Optional<PendingEmailChange> pendingEmailChange(UUID userId) {
        return requests.findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(userId, now())
                .map(PendingEmailChange::of);
    }

    @Transactional
    public void cancelEmailChange(UUID userId) {
        requests.cancelAll(userId, now());
    }

    /**
     * Applies a change from its emailed link. Works signed in or out: the
     * link proves access to the new address, the request proved the password.
     *
     * @return the new sign-in email
     */
    @Transactional
    public String confirmEmailChange(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidConfirmLinkException();
        }
        String hash = OneTimeTokens.hash(token.trim());
        Instant now = now();

        EmailChangeRequest request = requests.findByTokenHash(hash)
                .filter(candidate -> candidate.usableAt(now))
                .orElseThrow(InvalidConfirmLinkException::new);
        UUID userId = request.getUser().getId();
        String email = request.getNewEmail();

        // Someone may have taken the address since it was requested.
        requireUnused(email, userId);
        if (requests.use(hash, now) == 0) {
            throw new InvalidConfirmLinkException();
        }
        requests.cancelAll(userId, now);

        UserAccount user = findUser(userId);
        String oldEmail = user.getEmail();
        user.setEmail(email);
        users.saveAndFlush(user);

        events.publishEvent(new AccountEmail(oldEmail,
                "Your IncidentIQ email was changed",
                """
                Hi %s,

                The email you sign in to IncidentIQ with was changed from %s to %s.

                If this wasn't you, contact your IncidentIQ admin right away.
                """.formatted(user.getDisplayName(), oldEmail, email)));
        log.info("User {} confirmed a new sign-in email", userId);
        return email;
    }

    private void requireUnused(String email, UUID userId) {
        users.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw new DuplicateEmailException(email);
                });
    }

    /** {@code pending} is null when the change was applied straight away. */
    public record EmailChangeResult(UserResponse user, PendingEmailChange pending) {
    }

    public record PendingEmailChange(String newEmail, Instant expiresAt) {

        static PendingEmailChange of(EmailChangeRequest request) {
            return new PendingEmailChange(request.getNewEmail(), request.getExpiresAt());
        }
    }
}
