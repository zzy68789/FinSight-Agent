package com.zzy.finsight.service.impl;

import com.zzy.finsight.rag.PdfTextExtractor;
import com.zzy.finsight.rag.RagDocument;
import com.zzy.finsight.rag.RagDocumentChunk;
import com.zzy.finsight.rag.TextChunker;
import com.zzy.finsight.rag.VectorDocumentStore;
import com.zzy.finsight.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceImplTest {

    @Test
    void processStoresPdfChunksInVectorStore() {
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        VectorDocumentStore vectorDocumentStore = mock(VectorDocumentStore.class);
        RagService ragService = new RagServiceImpl(pdfTextExtractor, vectorDocumentStore, new TextChunker(12, 2));
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "agent.pdf",
                "application/pdf",
                "fake-pdf".getBytes(StandardCharsets.UTF_8)
        );
        when(pdfTextExtractor.extract(any())).thenReturn("agent workflow needs vector rag");

        int stored = ragService.process(List.of(file));

        assertThat(stored).isEqualTo(3);
        verify(vectorDocumentStore).clear();
        verify(vectorDocumentStore).add(eq(List.of(
                new RagDocumentChunk("agent.pdf", 0, "agent workfl"),
                new RagDocumentChunk("agent.pdf", 1, "flow needs v"),
                new RagDocumentChunk("agent.pdf", 2, " vector rag")
        )));
    }

    @Test
    void retrieveDelegatesToVectorStore() {
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        VectorDocumentStore vectorDocumentStore = mock(VectorDocumentStore.class);
        RagService ragService = new RagServiceImpl(pdfTextExtractor, vectorDocumentStore, new TextChunker(12, 2));
        when(vectorDocumentStore.query(eq("agent"), anyInt()))
                .thenReturn(List.of(new RagDocument("agent.pdf", "agent content", 0.95)));

        List<RagDocument> docs = ragService.retrieve("agent", 2);

        assertThat(docs).containsExactly(new RagDocument("agent.pdf", "agent content", 0.95));
    }

    @Test
    void indexTextAppendsReportChunksWithoutClearingVectorStore() {
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        VectorDocumentStore vectorDocumentStore = mock(VectorDocumentStore.class);
        RagService ragService = new RagServiceImpl(pdfTextExtractor, vectorDocumentStore, new TextChunker(12, 2));

        int stored = ragService.indexText("report-thread-1-v2.md", "agent report content");

        assertThat(stored).isEqualTo(2);
        verify(vectorDocumentStore).add(eq(List.of(
                new RagDocumentChunk("report-thread-1-v2.md", 0, "agent report"),
                new RagDocumentChunk("report-thread-1-v2.md", 1, "rt content")
        )));
        verify(vectorDocumentStore, never()).clear();
    }
}
