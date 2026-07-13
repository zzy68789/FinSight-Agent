package com.zzy.finsight.dto;

import java.time.LocalDateTime;

/**
 * 表示管理员视角下的报告信息。
 * @param id 主键标识。
 * @param ownerId 所属用户标识。
 * @param ownerUsername 所属用户名。
 * @param taskId 关联任务标识。
 * @param threadId 会话标识。
 * @param content 正文内容。
 * @param version 报告版本号。
 * @param reviewStatus 报告审查状态。
 * @param favorite 是否已收藏。
 * @param indexedAt 加入知识库的时间。
 * @param createdAt 创建时间。
 */
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
