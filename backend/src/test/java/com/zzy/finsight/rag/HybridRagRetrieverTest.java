package com.zzy.finsight.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRagRetrieverTest {

    @Test
    void combinesBm25AndVectorResultsWithDeduplication() {
        StubVectorDocumentStore vectorStore = new StubVectorDocumentStore(List.of(
                new RagDocument("agent.pdf", "agent workflow planning and reviewer loop", 0.60),
                new RagDocument("vector.pdf", "semantic context from vector store", 0.88)
        ));
        HybridRagRetriever retriever = new HybridRagRetriever(vectorStore, 0.20);
        retriever.index(List.of(
                new RagDocumentChunk("agent.pdf", 0, "agent workflow planning and reviewer loop"),
                new RagDocumentChunk("bm25.pdf", 0, "agent workflow uses planner researcher writer reviewer")
        ));

        List<RagDocument> docs = retriever.retrieve("agent workflow reviewer", 5);

        assertThat(docs)
                .extracting(RagDocument::source)
                .containsExactlyInAnyOrder("bm25.pdf", "agent.pdf", "vector.pdf");
        assertThat(docs)
                .extracting(RagDocument::score)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(docs)
                .extracting(RagDocument::content)
                .doesNotHaveDuplicates();
    }

    @Test
    void filtersWeakResultsByRelevanceThreshold() {
        StubVectorDocumentStore vectorStore = new StubVectorDocumentStore(List.of(
                new RagDocument("weak.pdf", "barely related", 0.10),
                new RagDocument("strong.pdf", "agent workflow context", 0.45)
        ));
        HybridRagRetriever retriever = new HybridRagRetriever(vectorStore, 0.20);

        List<RagDocument> docs = retriever.retrieve("agent workflow", 5);

        assertThat(docs)
                .extracting(RagDocument::source)
                .containsExactly("strong.pdf");
    }

    @Test
    void exposesChannelScoresAndFilteringStatistics() {
        StubVectorDocumentStore vectorStore = new StubVectorDocumentStore(List.of(
                new RagDocument("shared.pdf", "agent workflow reviewer", 0.70),
                new RagDocument("weak.pdf", "weak", 0.10)
        ));
        HybridRagRetriever retriever = new HybridRagRetriever(vectorStore, 0.20);
        retriever.index(List.of(new RagDocumentChunk("shared.pdf", 0, "agent workflow reviewer")));

        RagRetrievalResult result = retriever.retrieveWithTrace("agent workflow", 5);

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.filteredCount()).isEqualTo(1);
        assertThat(result.traceEntries().get(0).channels()).containsExactly("keyword", "vector");
        assertThat(result.traceEntries().get(0).fusionScore()).isGreaterThanOrEqualTo(0.70);
        assertThat(result.traceEntries().get(0).chunkId()).isEqualTo(result.documents().get(0).chunkId());
    }

    @Test
    void generatesStableChunkIdsFromSourceIndexAndContent() {
        RagDocumentChunk first = new RagDocumentChunk("report.md", 2, "稳定正文");
        RagDocumentChunk same = new RagDocumentChunk("report.md", 2, "稳定正文");
        RagDocumentChunk differentIndex = new RagDocumentChunk("report.md", 3, "稳定正文");

        assertThat(first.chunkId()).isEqualTo(same.chunkId());
        assertThat(first.chunkId()).hasSize(64);
        assertThat(first.chunkId()).isNotEqualTo(differentIndex.chunkId());
    }

    private static class StubVectorDocumentStore implements VectorDocumentStore {
        private final List<RagDocument> queryResults;

        private StubVectorDocumentStore(List<RagDocument> queryResults) {
            this.queryResults = queryResults;
        }

        @Override
        public void add(List<RagDocumentChunk> chunks) {
        }

        @Override
        public List<RagDocument> query(String query, int topK) {
            return queryResults.stream().limit(topK).toList();
        }

        @Override
        public void clear() {
        }
    }
}
