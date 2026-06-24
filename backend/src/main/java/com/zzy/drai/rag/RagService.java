package com.zzy.drai.rag;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RagService {
    private final PdfTextExtractor pdfTextExtractor;
    private final TextChunker textChunker;
    private final List<RagDocument> documents = new CopyOnWriteArrayList<>();

    public RagService(PdfTextExtractor pdfTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.textChunker = new TextChunker(500, 50);
    }

    public int process(List<MultipartFile> files) {
        clear();
        int stored = 0;
        for (MultipartFile file : files) {
            stored += processOne(file);
        }
        return stored;
    }

    public List<RagDocument> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || documents.isEmpty()) {
            return List.of();
        }
        List<String> queryTerms = tokenize(query);
        return documents.stream()
                .map(doc -> new RagDocument(doc.source(), doc.content(), score(queryTerms, doc.content())))
                .filter(doc -> doc.score() > 0)
                .sorted(Comparator.comparingDouble(RagDocument::score).reversed())
                .limit(topK)
                .toList();
    }

    public void clear() {
        documents.clear();
    }

    private int processOne(MultipartFile file) {
        if (file.isEmpty()) {
            return 0;
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("MVP 仅支持 PDF 文件");
        }
        try {
            String text = pdfTextExtractor.extract(file.getBytes());
            List<String> chunks = textChunker.chunk(text);
            chunks.forEach(chunk -> documents.add(new RagDocument(file.getOriginalFilename(), chunk, 1.0)));
            return chunks.size();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取上传文件失败: " + e.getMessage(), e);
        }
    }

    private double score(List<String> queryTerms, String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        double score = 0;
        for (String term : queryTerms) {
            if (normalized.contains(term.toLowerCase(Locale.ROOT))) {
                score += 1;
            }
        }
        return score;
    }

    private List<String> tokenize(String query) {
        String[] pieces = query.split("[\\s,，。；;：:、]+");
        List<String> terms = new ArrayList<>();
        for (String piece : pieces) {
            String trimmed = piece.trim();
            if (!trimmed.isBlank()) {
                terms.add(trimmed);
            }
        }
        if (terms.isEmpty()) {
            terms.add(query);
        }
        return terms;
    }
}
