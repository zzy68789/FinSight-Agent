package com.zzy.finsight.dto;

import java.time.LocalDateTime;

public record AdminTaskResponse(
        long id,
        long ownerId,
        String ownerUsername,
        String threadId,
        String query,
        String searchMode,
        String status,
        int revisionNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
