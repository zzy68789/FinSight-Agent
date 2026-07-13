package com.zzy.finsight.dto;

/**
 * 表示用户认证结果。
 * @param userId 用户标识。
 * @param username 用户名。
 * @param email 用户邮箱。
 * @param role 用户角色。
 * @param token 访问令牌。
 */
public record AuthResponse(
        long userId,
        String username,
        String email,
        String role,
        String token
) {
}
