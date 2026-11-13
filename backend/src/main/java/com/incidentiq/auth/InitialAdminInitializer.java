package com.incidentiq.auth;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/**
 * Creates the first admin on a brand-new database from
 * {@code incidentiq.bootstrap.admin-*} (INITIAL_ADMIN_EMAIL /
 * INITIAL_ADMIN_PASSWORD). It does nothing once any account exists, so the
 * values can't be used to take over or reset an existing install.
 */
// After DevAccountInitializer (dev only).
@Order(10)
@Component
public class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public InitialAdminInitializer(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${incidentiq.bootstrap.admin-email:}") String email,
            @Value("${incidentiq.bootstrap.admin-password:}") String password,
            @Value("${incidentiq.bootstrap.admin-name:Administrator}") String displayName) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
        this.displayName = displayName == null || displayName.isBlank()
                ? "Administrator"
                : displayName.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        if (email.isEmpty() || password.isEmpty()) {
            log.warn("The database has no accounts and no initial admin is configured,"
                    + " so nobody can sign in. Set INITIAL_ADMIN_EMAIL and"
                    + " INITIAL_ADMIN_PASSWORD and restart.");
            return;
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("INITIAL_ADMIN_EMAIL must be an email address");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > 72) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must be "
                    + MIN_PASSWORD_LENGTH + " to 72 characters");
        }

        UserAccount admin = new UserAccount(email, displayName, UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        repository.save(admin);
        log.info("Created the initial admin account {}. Sign in, then change the password"
                + " on the Account page and remove INITIAL_ADMIN_PASSWORD.", email);
    }
}
