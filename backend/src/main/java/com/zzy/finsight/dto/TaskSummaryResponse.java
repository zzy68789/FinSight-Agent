package com.zzy.finsight.dto;

import java.time.LocalDateTime;

public record TaskSummaryResponse(
        long id,
        String threadId,
        String query,
        String searchMode,
        String status,
        int revisionNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
