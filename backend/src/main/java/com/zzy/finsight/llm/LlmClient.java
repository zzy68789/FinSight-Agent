package com.zzy.finsight.llm;

/**
 * 定义大语言模型文本生成能力。
 */
public interface LlmClient {
    /** 使用指定模型档位生成文本。 */
    String generate(String prompt, ModelType modelType);

    enum ModelType {
        FAST,
        SMART
    }
}
