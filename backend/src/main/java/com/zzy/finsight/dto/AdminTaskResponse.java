package com.zzy.finsight.dto;

import java.time.LocalDateTime;

/**
 * 表示管理员视角下的任务信息。
 * @param id 主键标识。
 * @param ownerId 所属用户标识。
 * @param ownerUsername 所属用户名。
 * @param threadId 会话标识。
 * @param query 任务研究问题。
 * @param searchMode 检索模式。
 * @param status 当前状态。
 * @param revisionNumber 任务修订轮次。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
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
