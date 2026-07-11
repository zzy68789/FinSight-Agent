package com.zzy.finsight.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RagService {
    private final PdfTextExtractor pdfTextExtractor;
    private final HybridRagRetriever hybridRagRetriever;
    private final TextChunker textChunker;

    @Autowired
    public RagService(PdfTextExtractor pdfTextExtractor, HybridRagRetriever hybridRagRetriever) {
        this(pdfTextExtractor, hybridRagRetriever, new TextChunker(500, 50));
    }

    RagService(PdfTextExtractor pdfTextExtractor, VectorDocumentStore vectorDocumentStore, TextChunker textChunker) {
        this(pdfTextExtractor, new HybridRagRetriever(vectorDocumentStore, 0.2), textChunker);
    }

    RagService(PdfTextExtractor pdfTextExtractor, HybridRagRetriever hybridRagRetriever, TextChunker textChunker) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.hybridRagRetriever = hybridRagRetriever;
        this.textChunker = textChunker;
    }

    public int process(List<MultipartFile> files) {
        clear();
        int stored = 0;
        List<RagDocumentChunk> chunks = new ArrayList<>();
        for (MultipartFile file : files) {
            List<RagDocumentChunk> fileChunks = processOne(file);
            chunks.addAll(fileChunks);
            stored += fileChunks.size();
        }
        hybridRagRetriever.index(chunks);
        return stored;
    }

    public List<RagDocument> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return hybridRagRetriever.retrieve(query, topK);
    }

    public RagRetrievalResult retrieveWithTrace(String query, int topK) {
        return hybridRagRetriever.retrieveWithTrace(query, topK);
    }

    public int indexText(String source, String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        List<String> rawChunks = textChunker.chunk(text);
        List<RagDocumentChunk> chunks = new ArrayList<>(rawChunks.size());
        String normalizedSource = source == null || source.isBlank() ? "report.md" : source;
        for (int i = 0; i < rawChunks.size(); i++) {
            chunks.add(new RagDocumentChunk(normalizedSource, i, rawChunks.get(i)));
        }
        hybridRagRetriever.index(chunks);
        return chunks.size();
    }

    public void clear() {
        hybridRagRetriever.clear();
    }

    private List<RagDocumentChunk> processOne(MultipartFile file) {
        if (file.isEmpty()) {
            return List.of();
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("MVP only supports PDF files");
        }
        try {
            String text = pdfTextExtractor.extract(file.getBytes());
            List<String> rawChunks = textChunker.chunk(text);
            List<RagDocumentChunk> chunks = new ArrayList<>(rawChunks.size());
            for (int i = 0; i < rawChunks.size(); i++) {
                chunks.add(new RagDocumentChunk(file.getOriginalFilename(), i, rawChunks.get(i)));
            }
            return chunks;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage(), e);
        }
    }
}
