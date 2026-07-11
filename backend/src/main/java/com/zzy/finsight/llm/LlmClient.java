package com.zzy.finsight.llm;

public interface LlmClient {
    String generate(String prompt, ModelType modelType);

    enum ModelType {
        FAST,
        SMART
    }
}
