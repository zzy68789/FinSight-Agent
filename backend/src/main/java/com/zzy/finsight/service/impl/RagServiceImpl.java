package com.zzy.finsight.service.impl;

import com.zzy.finsight.rag.HybridRagRetriever;
import com.zzy.finsight.rag.PdfTextExtractor;
import com.zzy.finsight.rag.RagDocument;
import com.zzy.finsight.rag.RagDocumentChunk;
import com.zzy.finsight.rag.RagRetrievalResult;
import com.zzy.finsight.rag.TextChunker;
import com.zzy.finsight.rag.VectorDocumentStore;
import com.zzy.finsight.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 负责 RAG 文档入库、检索和向量存储降级的业务实现。
 */
@Service
public class RagServiceImpl implements RagService {
    private final PdfTextExtractor pdfTextExtractor;
    private final HybridRagRetriever hybridRagRetriever;
    private final TextChunker textChunker;

    @Autowired
    public RagServiceImpl(PdfTextExtractor pdfTextExtractor, HybridRagRetriever hybridRagRetriever) {
        this(pdfTextExtractor, hybridRagRetriever, new TextChunker(500, 50));
    }

    RagServiceImpl(PdfTextExtractor pdfTextExtractor, VectorDocumentStore vectorDocumentStore, TextChunker textChunker) {
        this(pdfTextExtractor, new HybridRagRetriever(vectorDocumentStore, 0.2), textChunker);
    }

    RagServiceImpl(PdfTextExtractor pdfTextExtractor, HybridRagRetriever hybridRagRetriever, TextChunker textChunker) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.hybridRagRetriever = hybridRagRetriever;
        this.textChunker = textChunker;
    }

    @Override
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

    @Override
    public List<RagDocument> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return hybridRagRetriever.retrieve(query, topK);
    }

    @Override
    public RagRetrievalResult retrieveWithTrace(String query, int topK) {
        return hybridRagRetriever.retrieveWithTrace(query, topK);
    }

    @Override
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

    @Override
    public void clear() {
        hybridRagRetriever.clear();
    }

    /** 解析单个 PDF 并保留原始文件名作为切片来源。 */
    private List<RagDocumentChunk> processOne(MultipartFile file) {
        if (file.isEmpty()) {
            return List.of();
        }
        if (file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
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
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + exception.getMessage(), exception);
        }
    }
}
