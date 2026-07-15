package com.zzy.finsight.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVectorDocumentStoreTenantIsolationTest {

    @Test
    void queryAndClearAreIsolatedByKnowledgeSpace() {
        InMemoryVectorDocumentStore store = new InMemoryVectorDocumentStore(text -> List.of(
                text.contains("alpha") ? 1.0d : 0.0d,
                text.contains("beta") ? 1.0d : 0.0d
        ));
        RagKnowledgeSpace userA = RagKnowledgeSpace.forOwner(7L);
        RagKnowledgeSpace userB = RagKnowledgeSpace.forOwner(8L);
        store.add(userA, List.of(new RagDocumentChunk("a.pdf", 0, "alpha evidence")));
        store.add(userB, List.of(new RagDocumentChunk("b.pdf", 0, "beta evidence")));
        store.add(userB, List.of(new RagDocumentChunk("b.pdf", 0, "beta evidence")));

        assertThat(store.query(userA, "alpha", 5)).extracting(RagDocument::source).containsExactly("a.pdf");
        assertThat(store.query(userA, "beta", 5)).isEmpty();
        assertThat(store.query(userB, "beta", 5)).extracting(RagDocument::source).containsExactly("b.pdf");

        store.clear(userA);

        assertThat(store.query(userA, "alpha", 5)).isEmpty();
        assertThat(store.query(userB, "beta", 5)).extracting(RagDocument::source).containsExactly("b.pdf");
    }
}
