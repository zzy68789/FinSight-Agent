package com.zzy.finsight.controller;

import com.zzy.finsight.auth.AuthenticatedUser;
import com.zzy.finsight.auth.AuthService;
import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.AuthRequest;
import com.zzy.finsight.dto.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserContext userContext;

    public AuthController(AuthService authService, UserContext userContext) {
        this.authService = authService;
        this.userContext = userContext;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.success(toResponse(authService.register(request.username(), request.email(), request.password())));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.success(toResponse(authService.login(request.username(), request.password())));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me() {
        return ApiResponse.success(toResponse(userContext.currentUser()));
    }

    private AuthResponse toResponse(AuthenticatedUser user) {
        return new AuthResponse(user.userId(), user.username(), user.email(), user.role(), user.token());
    }
}
