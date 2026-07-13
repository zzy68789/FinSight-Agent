package com.zzy.finsight.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 从上传的 PDF 文件中提取正文文本。
 */
@Component
public class PdfTextExtractor {
    public String extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF 解析失败: " + e.getMessage(), e);
        }
    }
}
