package com.incidentiq.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.user.UserAccountRepository;

/** Loads users by email for sign-in. Accounts without a password are skipped. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public AppUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return repository.findByEmailIgnoreCase(email.trim())
                .filter(user -> user.getPasswordHash() != null)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No sign-in for " + email));
    }
}
