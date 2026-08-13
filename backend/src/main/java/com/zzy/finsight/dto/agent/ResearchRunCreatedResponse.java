package com.zzy.finsight.dto.agent;

/**
 * 表示 Research Agent 任务创建后的持久化回执。
 *
 * @param taskId 已持久化的任务标识。
 * @param threadId 任务所属研究线程标识。
 * @param status 任务当前状态。
 * @param reused 是否由同一客户端幂等键复用已有任务。
 */
public record ResearchRunCreatedResponse(
        long taskId,
        String threadId,
        String status,
        boolean reused
) {
}
