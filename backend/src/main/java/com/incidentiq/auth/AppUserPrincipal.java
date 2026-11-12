package com.incidentiq.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserRole;

/**
 * The signed-in user as stored in the session. Holds the account id so
 * controllers can attribute changes without trusting anything the client
 * sends. Kept small and serializable for the HTTP session.
 */
public record AppUserPrincipal(
        UUID id,
        String email,
        String passwordHash,
        UserRole role) implements UserDetails {

    public static AppUserPrincipal from(UserAccount user) {
        return new AppUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Keep the hash out of logs. */
    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
