package com.zzy.finsight.llm;

/**
 * 表示一次大模型生成结果及其可观测元数据。
 * @param text 生成正文。
 * @param modelName 实际模型名称，未知时为空字符串。
 * @param inputTokens 输入 Token 数，未知时为零。
 * @param outputTokens 输出 Token 数，未知时为零。
 * @param totalTokens 总 Token 数，未知时为零。
 * @param finishReason 模型结束原因，未知时为 UNKNOWN。
 * @param durationMs 调用耗时，单位毫秒。
 */
public record LlmGenerationResult(
        String text,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String finishReason,
        long durationMs
) {
    public LlmGenerationResult {
        text = text == null ? "" : text;
        modelName = modelName == null ? "" : modelName;
        finishReason = finishReason == null || finishReason.isBlank() ? "UNKNOWN" : finishReason;
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        totalTokens = Math.max(0, totalTokens);
        durationMs = Math.max(0L, durationMs);
    }
}
