package com.incidentiq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/**
 * Development only, so a local database is always usable:
 * <ul>
 * <li>An empty database gets a first admin account (adding members needs an
 * admin, so there would otherwise be no way in).</li>
 * <li>Accounts without a password get the configured default one.</li>
 * </ul>
 * Runs only when {@code incidentiq.auth.dev-default-password} is set (see the
 * "dev" profile); never set it in production. Accounts that already have a
 * password are never touched.
 */
// Before InitialAdminInitializer, so a dev database is never "empty" to it.
@Order(0)
@Component
@ConditionalOnExpression("'${incidentiq.auth.dev-default-password:}' != ''")
public class DevAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountInitializer.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;
    private final String adminEmail;

    public DevAccountInitializer(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${incidentiq.auth.dev-default-password}") String defaultPassword,
            @Value("${incidentiq.auth.dev-admin-email:admin@incidentiq.test}") String adminEmail) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() == 0) {
            UserAccount admin = new UserAccount(adminEmail, "Dev Admin", UserRole.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
            repository.save(admin);
            log.warn("Empty database: created development admin {} with the default password."
                    + " Do not enable the dev profile in production.", adminEmail);
            return;
        }

        int updated = 0;
        for (UserAccount user : repository.findAll()) {
            if (user.getPasswordHash() == null) {
                user.setPasswordHash(passwordEncoder.encode(defaultPassword));
                updated++;
            }
        }

        if (updated > 0) {
            log.warn("Set the development default password on {} account(s) without one."
                    + " Do not enable the dev profile in production.", updated);
        }
    }
}
