package com.zzy.finsight.rag;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 在进程内存中保存和检索向量文档。
 */
public class InMemoryVectorDocumentStore implements VectorDocumentStore {
    private final EmbeddingClient embeddingClient;
    private final List<StoredChunk> chunks = new CopyOnWriteArrayList<>();

    public InMemoryVectorDocumentStore(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void add(List<RagDocumentChunk> chunks) {
        for (RagDocumentChunk chunk : chunks) {
            this.chunks.add(new StoredChunk(chunk, embeddingClient.embed(chunk.content())));
        }
    }

    @Override
    public List<RagDocument> query(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0 || chunks.isEmpty()) {
            return List.of();
        }
        List<Double> queryEmbedding = embeddingClient.embed(query);
        return chunks.stream()
                .map(chunk -> new RagDocument(
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
    public void clear() {
        chunks.clear();
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

    private record StoredChunk(RagDocumentChunk documentChunk, List<Double> embedding) {
    }
}
