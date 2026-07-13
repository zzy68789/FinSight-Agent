package com.zzy.finsight.service.impl;

import com.zzy.finsight.auth.AuthException;
import com.zzy.finsight.auth.AuthenticatedUser;
import com.zzy.finsight.auth.PasswordHasher;
import com.zzy.finsight.auth.TokenClaims;
import com.zzy.finsight.auth.TokenService;
import com.zzy.finsight.domain.AppUserRecord;
import com.zzy.finsight.mapper.AppUserMapper;
import com.zzy.finsight.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {
    private final AppUserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthServiceImpl(AppUserMapper userMapper, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public AuthenticatedUser register(String username, String email, String password) {
        validate(username, password);
        if (userMapper.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUserRecord user = userMapper.create(username, email, passwordHasher.hash(password), "USER");
        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser login(String username, String password) {
        AppUserRecord user = userMapper.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        ensureActive(user);
        if (!passwordHasher.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        userMapper.updateLastLoginAt(user.id());
        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser resolveToken(String token) {
        TokenClaims claims = tokenService.verify(token);
        AppUserRecord user = userMapper.findById(claims.userId())
                .orElseThrow(() -> new AuthException("User not found"));
        ensureActive(user);
        return new AuthenticatedUser(user.id(), user.username(), user.email(), user.role(), token);
    }

    private AuthenticatedUser toAuthenticatedUser(AppUserRecord user) {
        String token = tokenService.issue(user.id(), user.username(), user.role());
        return new AuthenticatedUser(user.id(), user.username(), user.email(), user.role(), token);
    }

    private void validate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required and password must contain at least 6 characters");
        }
    }

    private void ensureActive(AppUserRecord user) {
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User disabled");
        }
    }
}
