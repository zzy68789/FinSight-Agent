package com.zzy.finsight.auth;

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
