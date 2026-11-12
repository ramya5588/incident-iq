package com.incidentiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

class DevAccountInitializerTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final DevAccountInitializer initializer = new DevAccountInitializer(
            repository, encoder, "dev-password", "admin@incidentiq.test");

    @Test
    void emptyDatabaseGetsAnAdminWithTheDefaultPassword() {
        when(repository.count()).thenReturn(0L);

        initializer.run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        UserAccount admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@incidentiq.test");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(encoder.matches("dev-password", admin.getPasswordHash())).isTrue();
    }

    @Test
    void fillsMissingPasswordsAndLeavesExistingOnesAlone() {
        UserAccount withoutPassword = new UserAccount("a@incidentiq.test", "A", UserRole.AGENT);
        UserAccount withPassword = new UserAccount("b@incidentiq.test", "B", UserRole.USER);
        withPassword.setPasswordHash("existing-hash");
        when(repository.count()).thenReturn(2L);
        when(repository.findAll()).thenReturn(List.of(withoutPassword, withPassword));

        initializer.run(null);

        assertThat(encoder.matches("dev-password", withoutPassword.getPasswordHash())).isTrue();
        assertThat(withPassword.getPasswordHash()).isEqualTo("existing-hash");
        verify(repository, never()).save(any());
    }
}
