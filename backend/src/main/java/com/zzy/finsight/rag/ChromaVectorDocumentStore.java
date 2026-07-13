package com.zzy.finsight.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 ChromaDB 持久化和检索向量文档。
 */
@Service
public class ChromaVectorDocumentStore implements VectorDocumentStore {
    private final RestClient restClient;
    private final EmbeddingClient embeddingClient;
    private final InMemoryVectorDocumentStore fallbackStore;
    private final String tenant;
    private final String database;
    private final String collectionName;
    private final String token;
    private volatile String collectionId;
    private volatile boolean chromaAvailable = true;

    public ChromaVectorDocumentStore(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            EmbeddingClient embeddingClient,
            @Value("${finsight.chroma.base-url:http://localhost:8000}") String baseUrl,
            @Value("${finsight.chroma.tenant:default_tenant}") String tenant,
            @Value("${finsight.chroma.database:default_database}") String database,
            @Value("${finsight.chroma.collection:finsight_docs}") String collectionName,
            @Value("${finsight.chroma.token:}") String token
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.embeddingClient = embeddingClient;
        this.fallbackStore = new InMemoryVectorDocumentStore(embeddingClient);
        this.tenant = tenant;
        this.database = database;
        this.collectionName = collectionName;
        this.token = token;
    }

    @Override
    public void add(List<RagDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        fallbackStore.add(chunks);
        if (!chromaAvailable) {
            return;
        }
        try {
            String targetCollectionId = ensureCollection();
            restClient.post()
                    .uri(collectionRecordsPath(targetCollectionId) + "/add")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::setToken)
                    .body(addRequest(chunks))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            markChromaUnavailable();
        }
    }

    @Override
    public List<RagDocument> query(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        if (!chromaAvailable) {
            return fallbackStore.query(query, topK);
        }
        try {
            String targetCollectionId = ensureCollection();
            JsonNode response = restClient.post()
                    .uri(collectionRecordsPath(targetCollectionId) + "/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::setToken)
                    .body(Map.of(
                            "query_embeddings", List.of(embeddingClient.embed(query)),
                            "n_results", topK,
                            "include", List.of("documents", "metadatas", "distances")
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            List<RagDocument> docs = parseQueryResponse(response);
            return docs.isEmpty() ? fallbackStore.query(query, topK) : docs;
        } catch (Exception e) {
            markChromaUnavailable();
            return fallbackStore.query(query, topK);
        }
    }

    @Override
    public void clear() {
        fallbackStore.clear();
        String targetCollectionId = collectionId == null || collectionId.isBlank() ? collectionName : collectionId;
        try {
            restClient.delete()
                    .uri(collectionRecordsPath(targetCollectionId))
                    .headers(this::setToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // ChromaDB 离线时仍允许清理本地状态。
        } finally {
            collectionId = null;
            chromaAvailable = true;
        }
    }

    private String ensureCollection() {
        if (collectionId != null && !collectionId.isBlank()) {
            return collectionId;
        }
        JsonNode response = restClient.post()
                .uri(collectionsPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::setToken)
                .body(Map.of(
                        "name", collectionName,
                        "get_or_create", true,
                        "metadata", Map.of("project", "finsight")
                ))
                .retrieve()
                .body(JsonNode.class);
        String id = response == null ? "" : response.path("id").asText();
        collectionId = id == null || id.isBlank() ? collectionName : id;
        return collectionId;
    }

    private Map<String, Object> addRequest(List<RagDocumentChunk> chunks) {
        List<String> ids = new ArrayList<>(chunks.size());
        List<List<Double>> embeddings = new ArrayList<>(chunks.size());
        List<String> documents = new ArrayList<>(chunks.size());
        List<Map<String, Object>> metadatas = new ArrayList<>(chunks.size());
        for (RagDocumentChunk chunk : chunks) {
            ids.add(chunk.source() + ":" + chunk.chunkIndex());
            embeddings.add(embeddingClient.embed(chunk.content()));
            documents.add(chunk.content());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", chunk.source());
            metadata.put("chunk_index", chunk.chunkIndex());
            metadatas.add(metadata);
        }
        return Map.of(
                "ids", ids,
                "embeddings", embeddings,
                "documents", documents,
                "metadatas", metadatas
        );
    }

    private List<RagDocument> parseQueryResponse(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        JsonNode documents = response.path("documents").path(0);
        JsonNode metadatas = response.path("metadatas").path(0);
        JsonNode distances = response.path("distances").path(0);
        if (!documents.isArray()) {
            return List.of();
        }
        List<RagDocument> results = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            String content = documents.path(i).asText("");
            String source = metadatas.path(i).path("source").asText(collectionName);
            double distance = distances.path(i).asDouble(1.0d);
            double score = Math.max(0.0d, 1.0d - distance);
            results.add(new RagDocument(source, content, round(score)));
        }
        return results;
    }

    private String collectionsPath() {
        return "/api/v2/tenants/%s/databases/%s/collections".formatted(tenant, database);
    }

    private String collectionRecordsPath(String targetCollectionId) {
        return collectionsPath() + "/" + targetCollectionId;
    }

    private void setToken(HttpHeaders headers) {
        if (token != null && !token.isBlank()) {
            headers.set("x-chroma-token", token);
        }
    }

    private void markChromaUnavailable() {
        collectionId = null;
        chromaAvailable = false;
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
