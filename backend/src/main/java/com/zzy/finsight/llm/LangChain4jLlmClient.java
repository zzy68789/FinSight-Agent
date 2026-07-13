package com.zzy.finsight.llm;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 通过 LangChain4j 调用兼容 OpenAI 的模型。
 */
public class LangChain4jLlmClient implements LlmClient {
    private final ChatModel fastChatModel;
    private final ChatModel smartChatModel;

    public LangChain4jLlmClient(ChatModel fastChatModel, ChatModel smartChatModel) {
        this.fastChatModel = fastChatModel;
        this.smartChatModel = smartChatModel;
    }

    @Override
    public String generate(String prompt, ModelType modelType) {
        ChatModel model = modelType == ModelType.SMART ? smartChatModel : fastChatModel;
        if (model == null) {
            throw new IllegalStateException("LLM is not configured");
        }
        String response = model.chat(prompt);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned blank response");
        }
        return response;
    }
}
