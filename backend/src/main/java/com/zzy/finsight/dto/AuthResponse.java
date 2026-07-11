package com.zzy.finsight.dto;

public record AuthResponse(
        long userId,
        String username,
        String email,
        String role,
        String token
) {
}
