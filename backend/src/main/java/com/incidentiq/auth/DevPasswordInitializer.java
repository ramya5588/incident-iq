package com.incidentiq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

/**
 * Development only: gives every account that has no password the configured
 * default one, so pre-existing local users can sign in. Runs only when
 * {@code incidentiq.auth.dev-default-password} is set (see the "dev"
 * profile); never set it in production. Accounts that already have a
 * password are never touched.
 */
@Component
@ConditionalOnExpression("'${incidentiq.auth.dev-default-password:}' != ''")
public class DevPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevPasswordInitializer.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public DevPasswordInitializer(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${incidentiq.auth.dev-default-password}") String defaultPassword) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (UserAccount user : repository.findAll()) {
            if (user.getPasswordHash() == null) {
                user.setPasswordHash(passwordEncoder.encode(defaultPassword));
                updated++;
            }
        }

        if (updated > 0) {
            log.warn("Set the development default password on {} account(s) without one."
                    + " Do not enable incidentiq.auth.dev-default-password in production.", updated);
        }
    }
}
