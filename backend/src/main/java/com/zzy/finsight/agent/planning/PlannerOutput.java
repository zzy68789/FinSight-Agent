package com.zzy.finsight.agent.planning;

/**
 * 包装 Planner 结果及模型调用元数据。
 * @param value 结构化计划或动作。
 * @param degraded 是否使用确定性降级决策。
 * @param degradedReason 降级原因。
 * @param inputTokens 输入 Token 数。
 * @param outputTokens 输出 Token 数。
 * @param durationMs Planner 调用耗时毫秒数。
 */
public record PlannerOutput<T>(
        T value,
        boolean degraded,
        String degradedReason,
        int inputTokens,
        int outputTokens,
        long durationMs
) {
    public PlannerOutput {
        degradedReason = degradedReason == null ? "" : degradedReason;
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        durationMs = Math.max(0L, durationMs);
    }

    /** 创建确定性降级输出。 */
    public static <T> PlannerOutput<T> degraded(T value, String reason) {
        return new PlannerOutput<>(value, true, reason, 0, 0, 0L);
    }
}
