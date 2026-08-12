package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示持久化的长任务恢复检查点。
 *
 * @param id 检查点标识
 * @param threadId 会话线程标识
 * @param taskId 任务标识
 * @param stage 任务阶段
 * @param attemptNo 阶段执行次数
 * @param generationContextHash 报告生成上下文指纹
 * @param stateVersion 状态结构版本
 * @param turnNo 检查点对应的已提交轮次
 * @param stateJson 阶段状态 JSON
 * @param createdAt 创建时间
 */
public record CheckpointRecord(
        long id,
        String threadId,
        long taskId,
        String stage,
        int attemptNo,
        String generationContextHash,
        String stateVersion,
        int turnNo,
        String stateJson,
        LocalDateTime createdAt
) {
    /** 兼容未写入 state_version 和 turn_no 的旧检查点 fixture。 */
    public CheckpointRecord(
            long id,
            String threadId,
            long taskId,
            String stage,
            int attemptNo,
            String generationContextHash,
            String stateJson,
            LocalDateTime createdAt
    ) {
        this(
                id, threadId, taskId, stage, attemptNo, generationContextHash,
                "agent-state-v1", 0, stateJson, createdAt
        );
    }
}
