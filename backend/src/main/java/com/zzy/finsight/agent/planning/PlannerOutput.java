package com.zzy.finsight.agent.planning;

/**
 * 包装 Planner 结果及模型调用元数据。
 * @param value 结构化计划或动作。
 * @param degraded 是否使用确定性降级决策。
 * @param degradedReason 降级原因。
 * @param inputTokens 输入 Token 数。
 * @param outputTokens 输出 Token 数。
 * @param durationMs Planner 调用耗时毫秒数。
 * @param decisionType Planner 决策类型。
 * @param requestedModel 请求的模型档位。
 * @param actualModel Provider 实际返回的模型名称。
 * @param structureAttempts 结构化输出尝试次数。
 * @param structuredValid 最终输出是否通过结构校验。
 */
public record PlannerOutput<T>(
        T value,
        boolean degraded,
        String degradedReason,
        int inputTokens,
        int outputTokens,
        long durationMs,
        String decisionType,
        String requestedModel,
        String actualModel,
        int structureAttempts,
        boolean structuredValid
) {
    public PlannerOutput {
        degradedReason = degradedReason == null ? "" : degradedReason;
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        durationMs = Math.max(0L, durationMs);
        decisionType = decisionType == null ? "" : decisionType;
        requestedModel = requestedModel == null ? "" : requestedModel;
        actualModel = actualModel == null ? "" : actualModel;
        structureAttempts = Math.max(0, structureAttempts);
    }

    /** 兼容不关心模型路由元数据的调用方。 */
    public PlannerOutput(
            T value,
            boolean degraded,
            String degradedReason,
            int inputTokens,
            int outputTokens,
            long durationMs
    ) {
        this(value, degraded, degradedReason, inputTokens, outputTokens, durationMs,
                "", "", "", 0, !degraded);
    }

    /** 创建确定性降级输出。 */
    public static <T> PlannerOutput<T> degraded(T value, String reason) {
        return degraded(value, reason, "");
    }

    /** 创建带决策类型的确定性降级输出。 */
    public static <T> PlannerOutput<T> degraded(T value, String reason, String decisionType) {
        return new PlannerOutput<>(value, true, reason, 0, 0, 0L,
                decisionType, "", "", 0, false);
    }
}
