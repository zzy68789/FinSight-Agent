package com.zzy.finsight.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientTest {

    @Test
    void retriesFailedModelCallAndReturnsSuccessfulResponse() {
        FailingOnceLlmClient delegate = new FailingOnceLlmClient();
        RetryingLlmClient client = new RetryingLlmClient(delegate, 2);

        String response = client.generate("prompt", LlmClient.ModelType.FAST);

        assertThat(response).isEqualTo("ok");
        assertThat(delegate.attempts).isEqualTo(2);
    }

    @Test
    void throwsLastFailureAfterAllAttemptsFail() {
        RetryingLlmClient client = new RetryingLlmClient((prompt, modelType) -> {
            throw new IllegalStateException("downstream unavailable");
        }, 2);

        assertThatThrownBy(() -> client.generate("prompt", LlmClient.ModelType.SMART))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("downstream unavailable");
    }

    private static class FailingOnceLlmClient implements LlmClient {
        private int attempts;

        @Override
        public String generate(String prompt, ModelType modelType) {
            attempts++;
            if (attempts == 1) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        }
    }
}
