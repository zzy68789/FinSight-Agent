package com.zzy.finsight.auth;

import com.zzy.finsight.domain.AppUserRecord;
import com.zzy.finsight.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final AppUserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(AppUserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public AuthenticatedUser register(String username, String email, String password) {
        validate(username, password);
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUserRecord user = userRepository.create(username, email, passwordHasher.hash(password), "USER");
        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser login(String username, String password) {
        AppUserRecord user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        ensureActive(user);
        if (!passwordHasher.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        userRepository.updateLastLoginAt(user.id());
        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser resolveToken(String token) {
        TokenClaims claims = tokenService.verify(token);
        AppUserRecord user = userRepository.findById(claims.userId())
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
