package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示持久化的报告版本。
 * @param id 主键标识。
 * @param taskId 关联任务标识。
 * @param threadId 会话标识。
 * @param content 正文内容。
 * @param version 报告版本号。
 * @param reviewStatus 报告审查状态。
 * @param critique 报告审查意见。
 * @param createdAt 创建时间。
 * @param favorite 是否已收藏。
 * @param indexedAt 加入知识库的时间。
 * @param snapshotId 金融快照标识。
 * @param dataSnapshotHash 数据快照摘要。
 * @param generationContextHash 报告生成上下文摘要。
 * @param reusedFromReportId 复用来源报告标识。
 */
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
