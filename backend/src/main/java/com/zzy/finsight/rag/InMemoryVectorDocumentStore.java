package com.zzy.finsight.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在进程内存中保存和检索向量文档。
 */
public class InMemoryVectorDocumentStore implements VectorDocumentStore {
    private final EmbeddingClient embeddingClient;
    private final Map<ScopedChunkKey, StoredChunk> chunks = new ConcurrentHashMap<>();

    public InMemoryVectorDocumentStore(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void add(RagKnowledgeSpace space, List<RagDocumentChunk> chunks) {
        for (RagDocumentChunk chunk : chunks) {
            this.chunks.put(
                    new ScopedChunkKey(space, chunk.chunkId()),
                    new StoredChunk(space, chunk, embeddingClient.embed(chunk.content()))
            );
        }
    }

    @Override
    public List<RagDocument> query(RagKnowledgeSpace space, String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0 || chunks.isEmpty()) {
            return List.of();
        }
        List<Double> queryEmbedding = embeddingClient.embed(query);
        return chunks.values().stream()
                .filter(chunk -> chunk.space().equals(space))
                .map(chunk -> new RagDocument(
                        chunk.documentChunk().chunkId(),
                        chunk.documentChunk().source(),
                        chunk.documentChunk().content(),
                        round(cosine(queryEmbedding, chunk.embedding()))
                ))
                .filter(doc -> doc.score() > 0)
                .sorted(Comparator.comparingDouble(RagDocument::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public void clear(RagKnowledgeSpace space) {
        chunks.keySet().removeIf(key -> key.space().equals(space));
    }

    private double cosine(List<Double> left, List<Double> right) {
        int dimension = Math.min(left.size(), right.size());
        if (dimension == 0) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < dimension; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private record StoredChunk(
            RagKnowledgeSpace space,
            RagDocumentChunk documentChunk,
            List<Double> embedding
    ) {
    }

    private record ScopedChunkKey(RagKnowledgeSpace space, String chunkId) {
    }
}
