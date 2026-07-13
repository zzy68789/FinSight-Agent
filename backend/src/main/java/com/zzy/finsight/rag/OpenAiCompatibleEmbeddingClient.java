package com.zzy.finsight.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调用兼容 OpenAI 的接口生成文本向量。
 */
@Service
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private static final int FALLBACK_DIMENSION = 128;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleEmbeddingClient(
            RestClient.Builder restClientBuilder,
            @Value("${finsight.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${finsight.llm.api-key:}") String apiKey,
            @Value("${finsight.embedding.model:text-embedding-3-small}") String model
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public List<Double> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackEmbedding(text);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", model,
                            "input", text == null ? "" : text
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode embedding = response == null ? null : response.path("data").path(0).path("embedding");
            if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                return fallbackEmbedding(text);
            }
            List<Double> vector = new ArrayList<>(embedding.size());
            embedding.forEach(value -> vector.add(value.asDouble()));
            return vector;
        } catch (Exception e) {
            return fallbackEmbedding(text);
        }
    }

    private List<Double> fallbackEmbedding(String text) {
        double[] vector = new double[FALLBACK_DIMENSION];
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(hash(token), FALLBACK_DIMENSION);
            vector[bucket] += 1.0d;
        }
        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(FALLBACK_DIMENSION);
        for (double value : vector) {
            result.add(norm == 0.0d ? 0.0d : value / norm);
        }
        return result;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        String[] pieces = text.toLowerCase().split("[\\s,，。；;：:、]+");
        List<String> tokens = new ArrayList<>();
        for (String piece : pieces) {
            if (!piece.isBlank()) {
                tokens.add(piece);
            }
        }
        return tokens.isEmpty() ? List.of(text) : tokens;
    }

    private int hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            int result = 0;
            for (int i = 0; i < Math.min(4, digest.length); i++) {
                result = (result << 8) | (digest[i] & 0xff);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
