package com.zzy.finsight.auth;

/**
 * 表示访问令牌中的用户声明。
 * @param userId 用户标识。
 * @param username 用户名。
 * @param role 用户角色。
 * @param expiresAtEpochSecond 令牌过期时间戳。
 */
public record TokenClaims(long userId, String username, String role, long expiresAtEpochSecond) {
}
