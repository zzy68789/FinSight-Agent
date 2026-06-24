package com.zzy.drai.domain;

import java.time.LocalDateTime;

public record ReportRecord(
        long id,
        Long taskId,
        String threadId,
        String content,
        int version,
        String reviewStatus,
        String critique,
        LocalDateTime createdAt
) {
}
