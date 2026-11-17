package com.incidentiq.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.notification.AccountEmail;
import com.incidentiq.notification.EmailSettings;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

/**
 * Slows down password guessing.
 * <ul>
 * <li>An account is locked for {@link #LOCK} after {@link #MAX_FAILURES}
 * wrong passwords within {@link #WINDOW}; while locked even the right password
 * is refused. Stored on the account, so it survives restarts and admins see
 * it.</li>
 * <li>Emails that don't belong to an account are counted the same way (in
 * memory), so a lockout never reveals whether an email has an account.</li>
 * <li>One address (IP) gets at most {@link #MAX_FAILURES_PER_ADDRESS} failures
 * within {@link #WINDOW}, whichever accounts they're for.</li>
 * </ul>
 */
@Service
public class SignInGuard {

    private static final Logger log = LoggerFactory.getLogger(SignInGuard.class);

    static final int MAX_FAILURES = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);
    /** Keeps the in-memory maps from growing without limit. */
    private static final int MAX_TRACKED = 10_000;

    private final UserAccountRepository users;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final EmailSettings emailSettings;

    /** Unknown emails: when each recent failure happened, and any lock. */
    private final Map<String, Attempts> unknownEmails = new ConcurrentHashMap<>();
    private final Map<String, Attempts> addresses = new ConcurrentHashMap<>();

    public SignInGuard(
            UserAccountRepository users,
            Clock clock,
            ApplicationEventPublisher events,
            EmailSettings emailSettings) {

        this.users = users;
        this.clock = clock;
        this.events = events;
        this.emailSettings = emailSettings;
    }

    /** Thrown when sign-in isn't allowed right now (429). */
    public static class LockedOutException extends TooManyRequestsException {
        public LockedOutException(String message) {
            super(message);
        }
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Call before checking a password. Throws if this address has failed too
     * often, or if the account (or unknown email) is locked.
     */
    @Transactional(readOnly = true)
    public void checkAllowed(String email, String address) {
        Instant now = clock.instant();
        Attempts fromAddress = addresses.get(address);
        if (fromAddress != null && fromAddress.recentCount(now) >= MAX_FAILURES_PER_ADDRESS) {
            throw new LockedOutException(
                    "Too many failed sign-ins from your network. Try again in a few minutes.");
        }
        Optional<Instant> lockedUntil = users.findByEmailIgnoreCase(key(email))
                .map(UserAccount::getLockedUntil)
                .or(() -> Optional.ofNullable(unknownEmails.get(key(email))).map(a -> a.lockedUntil));
        if (lockedUntil.isPresent() && lockedUntil.get().isAfter(now)) {
            throw lockedMessage(lockedUntil.get(), now);
        }
    }

    private static LockedOutException lockedMessage(Instant until, Instant now) {
        long minutes = Math.max(1, (Duration.between(now, until).toSeconds() + 59) / 60);
        return new LockedOutException("Too many failed sign-ins. Try again in " + minutes
                + " minute" + (minutes == 1 ? "" : "s") + ", or reset your password with \"Forgot password?\".");
    }

    /** A wrong password (or unknown email). */
    @Transactional
    public void recordFailure(String email, String address) {
        Instant now = clock.instant();
        trim(addresses);
        addresses.computeIfAbsent(address, ignored -> new Attempts()).add(now);

        Optional<UserAccount> account = users.findByEmailIgnoreCase(key(email));
        if (account.isPresent()) {
            recordFailure(account.get(), now);
        } else {
            trim(unknownEmails);
            Attempts attempts = unknownEmails.computeIfAbsent(key(email), ignored -> new Attempts());
            attempts.add(now);
            if (attempts.recentCount(now) >= MAX_FAILURES) {
                attempts.lockedUntil = now.plus(LOCK);
                attempts.clear();
            }
        }
    }

    /**
     * A wrong current password while signed in counts too (changing password
     * or email). Its own transaction, so the caller refusing the request
     * doesn't roll the count back.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordFailure(UserAccount user) {
        users.findById(user.getId()).ifPresent(found -> recordFailure(found, clock.instant()));
    }

    private void recordFailure(UserAccount user, Instant now) {
        boolean locked = user.recordFailedSignIn(now, WINDOW, MAX_FAILURES, LOCK);
        users.saveAndFlush(user);
        if (locked) {
            log.warn("Account {} locked after {} failed sign-ins", user.getId(), MAX_FAILURES);
            if (emailSettings.enabled() && user.isActive()) {
                events.publishEvent(new AccountEmail(user.getEmail(),
                        "Someone tried to sign in to your IncidentIQ account",
                        """
                        Hi %s,

                        There were %d failed attempts to sign in to your IncidentIQ account, so sign-in is
                        paused for %d minutes.

                        If this was you, wait and try again, or choose a new password:
                        %s

                        If it wasn't you, choose a new password to be safe.
                        """.formatted(user.getDisplayName(), MAX_FAILURES, LOCK.toMinutes(),
                                emailSettings.forgotPasswordLink())));
            }
        }
    }

    /** A successful sign-in: start counting again. */
    @Transactional
    public void recordSuccess(String email) {
        users.findByEmailIgnoreCase(key(email)).ifPresent(user -> {
            if (user.clearFailedSignIns()) {
                users.saveAndFlush(user);
            }
        });
    }

    /** Is this account locked out right now? */
    public boolean isLocked(UserAccount user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant());
    }

    /** For signed-in password checks: refuse while the account is locked. */
    public void checkNotLocked(UserAccount user) {
        if (isLocked(user)) {
            throw lockedMessage(user.getLockedUntil(), clock.instant());
        }
    }

    private static void trim(Map<String, Attempts> map) {
        if (map.size() > MAX_TRACKED) {
            map.clear();
        }
    }

    /** Recent failure times (oldest first) and an optional lock. */
    private static final class Attempts {
        private final Deque<Instant> times = new ArrayDeque<>();
        private volatile Instant lockedUntil;

        synchronized void add(Instant now) {
            times.addLast(now);
            prune(now);
        }

        synchronized int recentCount(Instant now) {
            prune(now);
            return times.size();
        }

        synchronized void clear() {
            times.clear();
        }

        private void prune(Instant now) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(now.minus(WINDOW))) {
                times.removeFirst();
            }
        }
    }
}
