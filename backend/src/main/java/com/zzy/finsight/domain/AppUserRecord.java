package com.zzy.finsight.domain;

import java.time.LocalDateTime;

public record AppUserRecord(
        long id,
        String username,
        String email,
        String passwordHash,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {
}
