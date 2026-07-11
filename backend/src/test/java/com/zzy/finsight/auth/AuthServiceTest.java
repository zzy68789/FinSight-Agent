package com.zzy.finsight.auth;

import com.zzy.finsight.domain.AppUserRecord;
import com.zzy.finsight.repository.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void registersUserAndReturnsResolvableToken() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordHasher passwordHasher = new PasswordHasher();
        TokenService tokenService = new TokenService("test-secret", 3600);
        AuthService authService = new AuthService(userRepository, passwordHasher, tokenService);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.create(org.mockito.ArgumentMatchers.eq("alice"), org.mockito.ArgumentMatchers.eq("alice@example.com"), anyString(), org.mockito.ArgumentMatchers.eq("USER")))
                .thenReturn(new AppUserRecord(7L, "alice", "alice@example.com", passwordHasher.hash("pass123"), "USER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), null));

        AuthenticatedUser user = authService.register("alice", "alice@example.com", "pass123");
        when(userRepository.findById(7L))
                .thenReturn(Optional.of(new AppUserRecord(7L, "alice", "alice@example.com", "", "USER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), null)));
        AuthenticatedUser resolved = authService.resolveToken(user.token());

        assertThat(user.userId()).isEqualTo(7L);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.role()).isEqualTo("USER");
        assertThat(resolved.userId()).isEqualTo(7L);
        assertThat(resolved.username()).isEqualTo("alice");
    }

    @Test
    void loginVerifiesPasswordBeforeIssuingToken() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordHasher passwordHasher = new PasswordHasher();
        TokenService tokenService = new TokenService("test-secret", 3600);
        AuthService authService = new AuthService(userRepository, passwordHasher, tokenService);
        String hash = passwordHasher.hash("pass123");

        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(new AppUserRecord(7L, "alice", "alice@example.com", hash, "USER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), null)));

        AuthenticatedUser user = authService.login("alice", "pass123");

        assertThat(user.token()).isNotBlank();
        assertThat(user.userId()).isEqualTo(7L);
    }
}
