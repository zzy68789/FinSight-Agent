package com.zzy.finsight.auth;

/**
 * 表示已通过认证的用户及其访问令牌。
 * @param userId 用户标识。
 * @param username 用户名。
 * @param email 用户邮箱。
 * @param role 用户角色。
 * @param token 访问令牌。
 */
public record AuthenticatedUser(
        long userId,
        String username,
        String email,
        String role,
        String token
) {
    public AuthenticatedUser withoutToken() {
        return new AuthenticatedUser(userId, username, email, role, "");
    }
}
