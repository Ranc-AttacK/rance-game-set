package com.rance.galcommunity.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.security.AppProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AdminBootstrapTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);
    private final AppProperties props = mock(AppProperties.class);

    private AdminBootstrap bootstrap(String email) {
        when(props.bootstrapAdminEmail()).thenReturn(email);
        return new AdminBootstrap(users, props);
    }

    @Test
    void promotesConfiguredEmailToAdmin() {
        UserAccount user = new UserAccount();
        user.setEmail("admin@example.com");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        bootstrap("  Admin@Example.com ").run(args);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(users).save(user);
    }

    @Test
    void leavesExistingAdminUntouched() {
        UserAccount user = new UserAccount();
        user.setEmail("admin@example.com");
        user.setRole(Role.ADMIN);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        bootstrap("admin@example.com").run(args);

        verify(users, never()).save(any());
    }

    @Test
    void refusesUnverifiedAccount() {
        UserAccount user = new UserAccount();
        user.setEmail("admin@example.com");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(false);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        bootstrap("admin@example.com").run(args);

        assertThat(user.getRole()).isEqualTo(Role.USER);
        verify(users, never()).save(any());
    }

    @Test
    void refusesWhenAnotherAdminExists() {
        UserAccount user = new UserAccount();
        user.setEmail("second@example.com");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        when(users.findByEmail("second@example.com")).thenReturn(Optional.of(user));
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap("second@example.com").run(args);

        assertThat(user.getRole()).isEqualTo(Role.USER);
        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenEmailBlank() {
        bootstrap(" ").run(args);

        verify(users, never()).findByEmail(any());
    }

    @Test
    void warnsWhenAccountNotFound() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        bootstrap("missing@example.com").run(args);

        verify(users, never()).save(any());
    }
}
