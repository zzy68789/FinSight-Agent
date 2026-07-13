package com.zzy.finsight.controller;

import com.zzy.finsight.auth.AuthenticatedUser;
import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.AuthRequest;
import com.zzy.finsight.dto.AuthResponse;
import com.zzy.finsight.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户注册、登录和身份查询接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserContext userContext;

    public AuthController(AuthService authService, UserContext userContext) {
        this.authService = authService;
        this.userContext = userContext;
    }

    /** 注册新用户并返回登录信息。 */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.success(toResponse(authService.register(request.username(), request.email(), request.password())));
    }

    /** 校验用户凭据并返回登录信息。 */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.success(toResponse(authService.login(request.username(), request.password())));
    }

    /** 返回当前登录用户信息。 */
    @GetMapping("/me")
    public ApiResponse<AuthResponse> me() {
        return ApiResponse.success(toResponse(userContext.currentUser()));
    }

    /** 将认证用户转换为接口响应。 */
    private AuthResponse toResponse(AuthenticatedUser user) {
        return new AuthResponse(user.userId(), user.username(), user.email(), user.role(), user.token());
    }
}
