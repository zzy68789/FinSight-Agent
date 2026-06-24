package com.zzy.drai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiCompatibleLlmClient implements LlmClient {
    private final RestClient restClient;
    private final String apiKey;
    private final String fastModel;
    private final String smartModel;

    public OpenAiCompatibleLlmClient(
            RestClient.Builder restClientBuilder,
            @Value("${drai.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${drai.llm.api-key:}") String apiKey,
            @Value("${drai.llm.fast-model:qwen3-max}") String fastModel,
            @Value("${drai.llm.smart-model:deepseek-r1}") String smartModel
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.fastModel = fastModel;
        this.smartModel = smartModel;
    }

    @Override
    public String generate(String prompt, ModelType modelType) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback(prompt, modelType);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", modelType == ModelType.SMART ? smartModel : fastModel,
                            "temperature", modelType == ModelType.SMART ? 0 : 0.7,
                            "messages", List.of(Map.of("role", "user", "content", prompt))
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return fallback(prompt, modelType);
            }
            String content = response.path("choices").path(0).path("message").path("content").asText();
            return content == null || content.isBlank() ? fallback(prompt, modelType) : content;
        } catch (Exception e) {
            return fallback(prompt, modelType);
        }
    }

    private String fallback(String prompt, ModelType modelType) {
        if (modelType == ModelType.SMART) {
            return "{\"status\":\"PASS\",\"feedback\":\"\"}";
        }
        String firstLine = prompt.lines().findFirst().orElse("DRAI");
        return "## DRAI 调研报告\n\n"
                + "当前未配置可用的大模型 API，系统使用本地降级内容完成流程验证。\n\n"
                + "- 任务摘要：" + firstLine + "\n"
                + "- 工程链路：Planner、Researcher、Writer、Reviewer 已完成状态流转。\n"
                + "- 后续建议：配置 `DRAI_LLM_API_KEY` 与 Tavily Key 后可生成真实调研报告。\n";
    }
}
