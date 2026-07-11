package com.zzy.finsight.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class HybridRagRetriever {
    private static final double BM25_K1 = 1.5d;
    private static final double BM25_B = 0.75d;

    private final VectorDocumentStore vectorDocumentStore;
    private final double relevanceThreshold;
    private final List<RagDocumentChunk> lexicalChunks = new CopyOnWriteArrayList<>();

    public HybridRagRetriever(
            VectorDocumentStore vectorDocumentStore,
            @Value("${finsight.rag.relevance-threshold:0.2}") double relevanceThreshold
    ) {
        this.vectorDocumentStore = vectorDocumentStore;
        this.relevanceThreshold = relevanceThreshold;
    }

    public void index(List<RagDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        lexicalChunks.addAll(chunks);
        vectorDocumentStore.add(chunks);
    }

    public List<RagDocument> retrieve(String query, int topK) {
        return retrieveWithTrace(query, topK).documents();
    }

    public RagRetrievalResult retrieveWithTrace(String query, int topK) {
        long startedAt = System.nanoTime();
        if (query == null || query.isBlank() || topK <= 0) {
            return RagRetrievalResult.empty(query, relevanceThreshold, elapsedMs(startedAt));
        }
        int candidateLimit = Math.max(topK * 2, topK);
        List<RagDocument> keywordCandidates = bm25(query, candidateLimit);
        List<RagDocument> vectorCandidates = vectorDocumentStore.query(query, candidateLimit);
        return mergeWithTrace(query, keywordCandidates, vectorCandidates, topK, startedAt);
    }

    public void clear() {
        lexicalChunks.clear();
        vectorDocumentStore.clear();
    }

    private List<RagDocument> bm25(String query, int topK) {
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty() || lexicalChunks.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> documentFrequency = documentFrequency(queryTerms);
        double averageLength = lexicalChunks.stream()
                .mapToInt(chunk -> Math.max(1, tokenize(chunk.content()).size()))
                .average()
                .orElse(1.0d);

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (RagDocumentChunk chunk : lexicalChunks) {
            double score = bm25Score(queryTerms, tokenize(chunk.content()), documentFrequency, averageLength);
            if (score > 0) {
                scoredChunks.add(new ScoredChunk(chunk, score));
            }
        }
        double maxScore = scoredChunks.stream().mapToDouble(ScoredChunk::score).max().orElse(1.0d);
        return scoredChunks.stream()
                .map(scored -> new RagDocument(scored.chunk().source(), scored.chunk().content(), round(scored.score() / maxScore)))
                .sorted(Comparator.comparingDouble(RagDocument::score).reversed())
                .limit(topK)
                .toList();
    }

    private double bm25Score(
            List<String> queryTerms,
            List<String> documentTerms,
            Map<String, Integer> documentFrequency,
            double averageLength
    ) {
        Map<String, Long> termFrequency = new HashMap<>();
        for (String term : documentTerms) {
            termFrequency.merge(term, 1L, Long::sum);
        }
        double score = 0.0d;
        int documentCount = lexicalChunks.size();
        int documentLength = Math.max(1, documentTerms.size());
        for (String queryTerm : queryTerms) {
            long frequency = termFrequency.getOrDefault(queryTerm, 0L);
            if (frequency == 0) {
                continue;
            }
            int df = documentFrequency.getOrDefault(queryTerm, 0);
            double idf = Math.log(1 + (documentCount - df + 0.5d) / (df + 0.5d));
            double denominator = frequency + BM25_K1 * (1 - BM25_B + BM25_B * documentLength / averageLength);
            score += idf * (frequency * (BM25_K1 + 1)) / denominator;
        }
        return score;
    }

    private Map<String, Integer> documentFrequency(List<String> queryTerms) {
        Map<String, Integer> df = new HashMap<>();
        Set<String> uniqueQueryTerms = new HashSet<>(queryTerms);
        for (RagDocumentChunk chunk : lexicalChunks) {
            Set<String> documentTerms = new HashSet<>(tokenize(chunk.content()));
            for (String queryTerm : uniqueQueryTerms) {
                if (documentTerms.contains(queryTerm)) {
                    df.merge(queryTerm, 1, Integer::sum);
                }
            }
        }
        return df;
    }

    private RagRetrievalResult mergeWithTrace(
            String query,
            List<RagDocument> keywordCandidates,
            List<RagDocument> vectorCandidates,
            int topK,
            long startedAt
    ) {
        Map<String, MutableTrace> candidates = new LinkedHashMap<>();
        for (RagDocument document : keywordCandidates) {
            candidates.computeIfAbsent(normalizeKey(document), key -> new MutableTrace(document))
                    .keywordScore = Math.max(document.score(), candidates.get(normalizeKey(document)).keywordScore);
        }
        for (RagDocument document : vectorCandidates) {
            candidates.computeIfAbsent(normalizeKey(document), key -> new MutableTrace(document))
                    .vectorScore = Math.max(document.score(), candidates.get(normalizeKey(document)).vectorScore);
        }

        List<MutableTrace> accepted = candidates.values().stream()
                .peek(trace -> trace.fusionScore = Math.max(trace.keywordScore, trace.vectorScore))
                .filter(trace -> trace.fusionScore >= relevanceThreshold)
                .sorted(Comparator.comparingDouble(MutableTrace::fusionScore).reversed())
                .limit(topK)
                .toList();
        List<RagRetrievalTraceEntry> entries = new ArrayList<>(accepted.size());
        List<RagDocument> documents = new ArrayList<>(accepted.size());
        for (int index = 0; index < accepted.size(); index++) {
            MutableTrace trace = accepted.get(index);
            List<String> channels = new ArrayList<>(2);
            if (trace.keywordScore > 0) {
                channels.add("keyword");
            }
            if (trace.vectorScore > 0) {
                channels.add("vector");
            }
            documents.add(new RagDocument(trace.document.source(), trace.document.content(), round(trace.fusionScore)));
            entries.add(new RagRetrievalTraceEntry(
                    trace.document.source(),
                    trace.document.content(),
                    round(trace.keywordScore),
                    round(trace.vectorScore),
                    round(trace.fusionScore),
                    index + 1,
                    channels
            ));
        }
        return new RagRetrievalResult(
                query,
                documents,
                entries,
                candidates.size(),
                entries.size(),
                Math.max(0, candidates.size() - entries.size()),
                keywordCandidates.size(),
                vectorCandidates.size(),
                relevanceThreshold,
                elapsedMs(startedAt)
        );
    }

    private String normalizeKey(RagDocument doc) {
        return doc.source().toLowerCase(Locale.ROOT).trim()
                + "::"
                + doc.content().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] pieces = text.toLowerCase(Locale.ROOT).split("[\\s,，。；;：:、()（）\\[\\]{}<>《》\"'`]+");
        List<String> terms = new ArrayList<>();
        for (String piece : pieces) {
            if (!piece.isBlank()) {
                terms.add(piece);
            }
        }
        return terms;
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record ScoredChunk(RagDocumentChunk chunk, double score) {
    }

    private static final class MutableTrace {
        private final RagDocument document;
        private double keywordScore;
        private double vectorScore;
        private double fusionScore;

        private MutableTrace(RagDocument document) {
            this.document = document;
        }

        private double fusionScore() {
            return fusionScore;
        }
    }
}
