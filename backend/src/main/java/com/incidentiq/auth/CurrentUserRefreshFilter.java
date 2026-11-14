package com.incidentiq.auth;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Re-reads the signed-in user from the database on every request, so the
 * session never acts on stale data:
 * <ul>
 * <li>a changed role applies on the very next request;</li>
 * <li>a deleted or deactivated account, or one whose password changed since
 * this session signed in (a reset, or a change made in another session), is
 * signed out.</li>
 * </ul>
 * The session that changes its own password is updated with the new hash
 * by the change-password endpoint, so it stays signed in.
 */
final class CurrentUserRefreshFilter extends OncePerRequestFilter {

    private final UserAccountRepository repository;

    CurrentUserRefreshFilter(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {

            Optional<UserAccount> user = repository.findById(principal.id());

            if (user.isEmpty()
                    || !user.get().isActive()
                    || !Objects.equals(user.get().getPasswordHash(), principal.passwordHash())) {
                signOut(request);
            } else if (user.get().getRole() != principal.role()
                    || !user.get().getEmail().equals(principal.email())) {
                SecurityContextHolder.getContext().setAuthentication(authenticated(user.get()));
            }
        }

        chain.doFilter(request, response);
    }

    static Authentication authenticated(UserAccount user) {
        AppUserPrincipal fresh = AppUserPrincipal.from(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                fresh, null, fresh.getAuthorities());
    }

    private static void signOut(HttpServletRequest request) {
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
