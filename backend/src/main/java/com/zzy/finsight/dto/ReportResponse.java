package com.zzy.finsight.dto;

import java.time.LocalDateTime;

/**
 * 表示对外返回的报告信息。
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
 */
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
