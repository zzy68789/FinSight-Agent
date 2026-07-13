package com.zzy.finsight.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 表示用户登录或注册请求。
 * @param username 用户名。
 * @param email 用户邮箱。
 * @param password 用户密码。
 */
public record AuthRequest(
        @NotBlank String username,
        String email,
        @NotBlank String password
) {
}
