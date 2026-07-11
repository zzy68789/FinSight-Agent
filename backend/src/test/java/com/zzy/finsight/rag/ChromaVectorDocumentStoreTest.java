package com.zzy.finsight.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChromaVectorDocumentStoreTest {

    @Test
    void addAndQueryUseChromaV2CollectionApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChromaVectorDocumentStore store = new ChromaVectorDocumentStore(
                builder,
                new ObjectMapper(),
                new StubEmbeddingClient(),
                "http://localhost:8000",
                "default_tenant",
                "default_database",
                "finsight_docs",
                "token"
        );

        server.expect(once(), requestTo("http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-chroma-token", "token"))
                .andExpect(content().string(containsString("\"name\":\"finsight_docs\"")))
                .andExpect(content().string(containsString("\"get_or_create\":true")))
                .andRespond(withSuccess("{\"id\":\"collection-id\",\"name\":\"finsight_docs\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections/collection-id/add"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"ids\":[\"agent.pdf:0\"]")))
                .andExpect(content().string(containsString("\"embeddings\":[[0.1,0.2,0.3]]")))
                .andExpect(content().string(containsString("\"documents\":[\"agent vector content\"]")))
                .andExpect(content().string(containsString("\"source\":\"agent.pdf\"")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections/collection-id/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"query_embeddings\":[[0.1,0.2,0.3]]")))
                .andExpect(content().string(containsString("\"n_results\":3")))
                .andExpect(content().string(containsString("\"include\":[\"documents\",\"metadatas\",\"distances\"]")))
                .andRespond(withSuccess("""
                        {
                          "documents": [["agent vector content"]],
                          "metadatas": [[{"source":"agent.pdf","chunk_index":0}]],
                          "distances": [[0.12]]
                        }
                        """, MediaType.APPLICATION_JSON));

        store.add(List.of(new RagDocumentChunk("agent.pdf", 0, "agent vector content")));
        List<RagDocument> docs = store.query("agent", 3);

        assertThat(docs).containsExactly(new RagDocument("agent.pdf", "agent vector content", 0.88));
        server.verify();
    }

    @Test
    void fallsBackToLocalVectorStoreWhenChromaIsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChromaVectorDocumentStore store = new ChromaVectorDocumentStore(
                builder,
                new ObjectMapper(),
                new StubEmbeddingClient(),
                "http://localhost:8000",
                "default_tenant",
                "default_database",
                "finsight_docs",
                ""
        );

        server.expect(once(), requestTo("http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections"))
                .andRespond(withServerError());

        store.add(List.of(new RagDocumentChunk("agent.pdf", 0, "agent vector content")));
        List<RagDocument> docs = store.query("agent", 1);

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).source()).isEqualTo("agent.pdf");
        assertThat(docs.get(0).content()).isEqualTo("agent vector content");
    }

    private static class StubEmbeddingClient implements EmbeddingClient {
        @Override
        public List<Double> embed(String text) {
            return List.of(0.1, 0.2, 0.3);
        }
    }
}
