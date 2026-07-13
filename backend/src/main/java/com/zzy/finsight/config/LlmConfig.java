package com.zzy.finsight.config;

import com.zzy.finsight.llm.LangChain4jLlmClient;
import com.zzy.finsight.llm.LlmClient;
import com.zzy.finsight.llm.RetryingLlmClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * 配置快速模型、智能模型及其客户端。
 */
@Configuration
public class LlmConfig {

    /**
     * fast（较高温度）聊天模型。未配置 API key 时返回 null，下游消费方必须做降级处理。
     */
    @Bean
    @Nullable
    ChatModel fastChatModel(
            @Value("${finsight.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${finsight.llm.api-key:}") String apiKey,
            @Value("${finsight.llm.fast-model:qwen3-max}") String fastModel,
            @Value("${finsight.llm.timeout:30s}") Duration timeout,
            @Value("${finsight.llm.provider-max-retries:0}") int providerMaxRetries,
            @Value("${finsight.llm.log-requests:false}") boolean logRequests,
            @Value("${finsight.llm.log-responses:false}") boolean logResponses
    ) {
        return buildModel(baseUrl, apiKey, fastModel, 0.7d, timeout, providerMaxRetries, logRequests, logResponses);
    }

    /**
     * smart（确定性，温度 0）聊天模型。未配置 API key 时返回 null，下游消费方必须做降级处理。
     */
    @Bean
    @Nullable
    ChatModel smartChatModel(
            @Value("${finsight.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${finsight.llm.api-key:}") String apiKey,
            @Value("${finsight.llm.smart-model:deepseek-r1}") String smartModel,
            @Value("${finsight.llm.timeout:30s}") Duration timeout,
            @Value("${finsight.llm.provider-max-retries:0}") int providerMaxRetries,
            @Value("${finsight.llm.log-requests:false}") boolean logRequests,
            @Value("${finsight.llm.log-responses:false}") boolean logResponses
    ) {
        return buildModel(baseUrl, apiKey, smartModel, 0.0d, timeout, providerMaxRetries, logRequests, logResponses);
    }

    @Bean
    LangChain4jLlmClient langChain4jLlmClient(
            @Nullable @Qualifier("fastChatModel") ChatModel fastChatModel,
            @Nullable @Qualifier("smartChatModel") ChatModel smartChatModel
    ) {
        return new LangChain4jLlmClient(fastChatModel, smartChatModel);
    }

    @Bean
    @Primary
    LlmClient llmClient(
            LangChain4jLlmClient langChain4jLlmClient,
            @Value("${finsight.llm.max-attempts:2}") int maxAttempts
    ) {
        return new RetryingLlmClient(langChain4jLlmClient, maxAttempts);
    }

    private ChatModel buildModel(
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature,
            Duration timeout,
            int maxRetries,
            boolean logRequests,
            boolean logResponses
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
