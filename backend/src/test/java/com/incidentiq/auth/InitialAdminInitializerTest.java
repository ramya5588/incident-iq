package com.incidentiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

class InitialAdminInitializerTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private InitialAdminInitializer initializer(String email, String password) {
        return new InitialAdminInitializer(repository, encoder, email, password, "Ops Lead");
    }

    @Test
    void createsTheFirstAdminOnAnEmptyDatabase() {
        when(repository.count()).thenReturn(0L);

        initializer("  Ops@Example.com ", "a-long-secret-123").run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ops@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Ops Lead");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(encoder.matches("a-long-secret-123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingOnceAnyAccountExists() {
        when(repository.count()).thenReturn(3L);

        initializer("ops@example.com", "a-long-secret-123").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void onlyWarnsWhenNothingIsConfigured() {
        when(repository.count()).thenReturn(0L);

        initializer("", "").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void refusesAWeakPasswordOrBadEmail() {
        when(repository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer("ops@example.com", "short").run(null))
                .hasMessageContaining("12 to 72");
        assertThatThrownBy(() -> initializer("not-an-email", "a-long-secret-123").run(null))
                .hasMessageContaining("email");
        verify(repository, never()).save(any());
    }
}
