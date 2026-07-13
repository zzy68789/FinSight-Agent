package com.zzy.finsight.service.impl;

import com.zzy.finsight.auth.AuthenticatedUser;
import com.zzy.finsight.auth.PasswordHasher;
import com.zzy.finsight.auth.TokenService;
import com.zzy.finsight.domain.AppUserRecord;
import com.zzy.finsight.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    @Test
    void registersUserAndReturnsResolvableToken() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        PasswordHasher passwordHasher = new PasswordHasher();
        TokenService tokenService = new TokenService("test-secret", 3600);
        AuthServiceImpl authService = new AuthServiceImpl(userMapper, passwordHasher, tokenService);

        when(userMapper.findByUsername("alice")).thenReturn(Optional.empty());
        when(userMapper.create(org.mockito.ArgumentMatchers.eq("alice"), org.mockito.ArgumentMatchers.eq("alice@example.com"), anyString(), org.mockito.ArgumentMatchers.eq("USER")))
                .thenReturn(new AppUserRecord(7L, "alice", "alice@example.com", passwordHasher.hash("pass123"), "USER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), null));

        AuthenticatedUser user = authService.register("alice", "alice@example.com", "pass123");
        when(userMapper.findById(7L))
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
        AppUserMapper userMapper = mock(AppUserMapper.class);
        PasswordHasher passwordHasher = new PasswordHasher();
        TokenService tokenService = new TokenService("test-secret", 3600);
        AuthServiceImpl authService = new AuthServiceImpl(userMapper, passwordHasher, tokenService);
        String hash = passwordHasher.hash("pass123");

        when(userMapper.findByUsername("alice"))
                .thenReturn(Optional.of(new AppUserRecord(7L, "alice", "alice@example.com", hash, "USER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), null)));

        AuthenticatedUser user = authService.login("alice", "pass123");

        assertThat(user.token()).isNotBlank();
        assertThat(user.userId()).isEqualTo(7L);
    }
}
