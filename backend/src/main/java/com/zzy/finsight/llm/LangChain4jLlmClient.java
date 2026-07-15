package com.zzy.finsight.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * 通过 LangChain4j 调用兼容 OpenAI 的模型。
 */
public class LangChain4jLlmClient implements LlmClient {
    private final ChatModel fastChatModel;
    private final ChatModel smartChatModel;
    private final ChatModel judgeChatModel;

    public LangChain4jLlmClient(ChatModel fastChatModel, ChatModel smartChatModel) {
        this(fastChatModel, smartChatModel, smartChatModel);
    }

    public LangChain4jLlmClient(ChatModel fastChatModel, ChatModel smartChatModel, ChatModel judgeChatModel) {
        this.fastChatModel = fastChatModel;
        this.smartChatModel = smartChatModel;
        this.judgeChatModel = judgeChatModel;
    }

    @Override
    public String generate(String prompt, ModelType modelType) {
        ChatModel model = model(modelType);
        if (model == null) {
            throw new IllegalStateException("LLM is not configured");
        }
        String response = model.chat(prompt);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned blank response");
        }
        return response;
    }

    @Override
    public LlmGenerationResult generateWithMetadata(String prompt, ModelType modelType) {
        ChatModel model = model(modelType);
        if (model == null) {
            throw new IllegalStateException("LLM is not configured");
        }
        long startedAt = System.nanoTime();
        ChatResponse response = model.chat(dev.langchain4j.data.message.UserMessage.from(prompt));
        String text = response == null || response.aiMessage() == null ? "" : response.aiMessage().text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("LLM returned blank response");
        }
        TokenUsage usage = response.tokenUsage();
        return new LlmGenerationResult(
                text,
                response.modelName(),
                tokenCount(usage == null ? null : usage.inputTokenCount()),
                tokenCount(usage == null ? null : usage.outputTokenCount()),
                tokenCount(usage == null ? null : usage.totalTokenCount()),
                response.finishReason() == null ? "UNKNOWN" : response.finishReason().name(),
                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)
        );
    }

    private ChatModel model(ModelType modelType) {
        if (modelType == ModelType.JUDGE) {
            return judgeChatModel;
        }
        return modelType == ModelType.SMART ? smartChatModel : fastChatModel;
    }

    private int tokenCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
