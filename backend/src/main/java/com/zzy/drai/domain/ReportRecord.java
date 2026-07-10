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
        LocalDateTime createdAt,
        boolean favorite,
        LocalDateTime indexedAt,
        Long snapshotId,
        String dataSnapshotHash,
        String generationContextHash,
        Long reusedFromReportId
) {
    public ReportRecord(
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
        this(id, taskId, threadId, content, version, reviewStatus, critique, createdAt, favorite, indexedAt,
                null, null, null, null);
    }
}
