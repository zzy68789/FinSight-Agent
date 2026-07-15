package com.zzy.finsight.llm;

/**
 * 为大模型调用提供有限次数重试。
 */
public class RetryingLlmClient implements LlmClient {
    private final LlmClient delegate;
    private final int maxAttempts;

    public RetryingLlmClient(LlmClient delegate, int maxAttempts) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public String generate(String prompt, ModelType modelType) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String response = delegate.generate(prompt, modelType);
                if (response != null && !response.isBlank()) {
                    return response;
                }
                lastFailure = new IllegalStateException("LLM returned blank response");
            } catch (RuntimeException e) {
                lastFailure = e;
            }
        }
        // 重试耗尽后抛出最后一次失败，由调用方决定如何降级
        throw lastFailure != null
                ? lastFailure
                : new IllegalStateException("LLM 调用失败且无可用响应");
    }

    @Override
    public LlmGenerationResult generateWithMetadata(String prompt, ModelType modelType) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LlmGenerationResult response = delegate.generateWithMetadata(prompt, modelType);
                if (response != null && !response.text().isBlank()) {
                    return response;
                }
                lastFailure = new IllegalStateException("LLM returned blank response");
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new IllegalStateException("LLM 调用失败且无可用响应");
    }
}
