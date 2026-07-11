package com.zzy.finsight.domain;

import java.time.LocalDateTime;

public record ResearchTaskRecord(
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
