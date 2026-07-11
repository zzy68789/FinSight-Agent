package com.zzy.finsight.dto;

import java.time.LocalDateTime;

public record AdminReportResponse(
        long id,
        long ownerId,
        String ownerUsername,
        Long taskId,
        String threadId,
        String content,
        int version,
        String reviewStatus,
        boolean favorite,
        LocalDateTime indexedAt,
        LocalDateTime createdAt
) {
}
