package com.incidentiq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.incidentiq.auth.AppUserPrincipal;
import com.incidentiq.user.UserAccount;

/**
 * Test helpers for signed-in API calls.
 *
 * <p>MockMvc requests run as whoever is in {@link TestSecurityContextHolder},
 * so tests call {@link #signInAs} instead of going through the login
 * endpoint. {@link Csrf} adds a valid CSRF token to every request; tests
 * that check CSRF itself build their own MockMvc without it.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static void signInAs(UserAccount user) {
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        signIn(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    public static void signIn(Authentication authentication) {
        TestSecurityContextHolder.setAuthentication(authentication);
    }

    /** The current test authentication, so a helper can restore it. */
    public static Authentication current() {
        return TestSecurityContextHolder.getContext().getAuthentication();
    }

    public static void signOut() {
        TestSecurityContextHolder.clearContext();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Csrf {

        @Bean
        MockMvcBuilderCustomizer csrfOnEveryRequest() {
            return builder -> builder.defaultRequest(
                    MockMvcRequestBuilders.get("/").with(csrf()));
        }
    }
}
