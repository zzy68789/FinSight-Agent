package com.zzy.finsight.llm;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
