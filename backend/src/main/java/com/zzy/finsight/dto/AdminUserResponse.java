package com.zzy.finsight.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        long id,
        String username,
        String email,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {
}
