package com.zzy.finsight.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class LangChain4jLlmClientTest {

    @Test
    void fastModelUsesFastChatModel() {
        ChatModel fastModel = mock(ChatModel.class);
        ChatModel smartModel = mock(ChatModel.class);
        LangChain4jLlmClient client = new LangChain4jLlmClient(fastModel, smartModel);
        when(fastModel.chat("plan")).thenReturn("fast response");

        String response = client.generate("plan", LlmClient.ModelType.FAST);

        assertThat(response).isEqualTo("fast response");
        verify(fastModel).chat("plan");
    }

    @Test
    void smartModelUsesSmartChatModel() {
        ChatModel fastModel = mock(ChatModel.class);
        ChatModel smartModel = mock(ChatModel.class);
        LangChain4jLlmClient client = new LangChain4jLlmClient(fastModel, smartModel);
        when(smartModel.chat("review")).thenReturn("{\"status\":\"PASS\",\"feedback\":\"\"}");

        String response = client.generate("review", LlmClient.ModelType.SMART);

        assertThat(response).contains("\"PASS\"");
        verify(smartModel).chat("review");
    }

    @Test
    void judgeModelReturnsStructuredGenerationMetadata() {
        ChatModel judgeModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        when(judgeModel.chat(any(dev.langchain4j.data.message.ChatMessage.class))).thenReturn(response);
        when(response.aiMessage()).thenReturn(dev.langchain4j.data.message.AiMessage.from("{\"relevance\":1}"));
        when(response.modelName()).thenReturn("judge-model");
        when(response.tokenUsage()).thenReturn(new TokenUsage(100, 20, 120));
        when(response.finishReason()).thenReturn(FinishReason.STOP);
        LangChain4jLlmClient client = new LangChain4jLlmClient(null, null, judgeModel);

        LlmGenerationResult result = client.generateWithMetadata("judge", LlmClient.ModelType.JUDGE);

        assertThat(result.modelName()).isEqualTo("judge-model");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(20);
        assertThat(result.totalTokens()).isEqualTo(120);
        assertThat(result.finishReason()).isEqualTo("STOP");
    }
}
