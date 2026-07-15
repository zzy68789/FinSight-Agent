package com.zzy.finsight.llm;

/**
 * 定义大语言模型文本生成能力。
 */
public interface LlmClient {
    /** 使用指定模型档位生成文本。 */
    String generate(String prompt, ModelType modelType);

    /** 使用指定模型档位生成文本并返回模型、Token 和耗时元数据。 */
    default LlmGenerationResult generateWithMetadata(String prompt, ModelType modelType) {
        long startedAt = System.nanoTime();
        String text = generate(prompt, modelType);
        return new LlmGenerationResult(
                text,
                "",
                0,
                0,
                0,
                "UNKNOWN",
                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)
        );
    }

    /** 选择报告草稿、复杂推理或独立 Judge 使用的模型档位。 */
    enum ModelType {
        FAST,
        SMART,
        JUDGE
    }
}
