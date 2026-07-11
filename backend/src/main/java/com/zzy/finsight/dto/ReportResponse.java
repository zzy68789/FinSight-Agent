package com.zzy.finsight.dto;

import java.time.LocalDateTime;

public record ReportResponse(
        long id,
        Long taskId,
        String threadId,
        String content,
        int version,
        String reviewStatus,
        String critique,
        LocalDateTime createdAt,
        boolean favorite,
        LocalDateTime indexedAt
) {
}
