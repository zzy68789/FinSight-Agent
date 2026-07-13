package com.zzy.finsight.service.impl;

import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.report.ExportedReport;
import com.zzy.finsight.service.ReportExportService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 实现 Markdown、PDF 和 Word 报告导出。
 */
@Service
public class ReportExportServiceImpl implements ReportExportService {
    private static final float FONT_SIZE = 11f;
    private static final float LINE_HEIGHT = 16f;
    private static final float MARGIN = 54f;

    public ExportedReport export(ReportResponse report, String format) {
        String normalizedFormat = normalizeFormat(format);
        String filename = filename(report, normalizedFormat);
        try {
            return switch (normalizedFormat) {
                case "pdf" -> new ExportedReport(filename, "application/pdf", toPdf(report));
                case "docx" -> new ExportedReport(
                        filename,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        toDocx(report)
                );
                case "md" -> new ExportedReport(filename, "text/markdown;charset=UTF-8", report.content().getBytes(StandardCharsets.UTF_8));
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported export format");
            };
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to export report", e);
        }
    }

    private byte[] toPdf(ReportResponse report) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FontSelection fontSelection = loadFont(document);
            PDFont font = fontSelection.font();
            List<String> lines = wrapLines(markdownToPlainText(report.content()), font, fontSelection.unicode());
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = openContentStream(document, page, font);
            float y = page.getMediaBox().getHeight() - MARGIN;
            for (String rawLine : lines) {
                if (y < MARGIN) {
                    contentStream.endText();
                    contentStream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = openContentStream(document, page, font);
                    y = page.getMediaBox().getHeight() - MARGIN;
                }
                contentStream.showText(fontSelection.unicode() ? rawLine : winAnsiSafe(rawLine));
                contentStream.newLineAtOffset(0, -LINE_HEIGHT);
                y -= LINE_HEIGHT;
            }
            contentStream.endText();
            contentStream.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private PDPageContentStream openContentStream(PDDocument document, PDPage page, PDFont font) throws IOException {
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.beginText();
        contentStream.setFont(font, FONT_SIZE);
        contentStream.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
        return contentStream;
    }

    private FontSelection loadFont(PDDocument document) {
        List<Path> candidates = List.of(
                Path.of("C:\\Windows\\Fonts\\simhei.ttf"),
                Path.of("C:\\Windows\\Fonts\\msyh.ttf"),
                Path.of("C:\\Windows\\Fonts\\arialuni.ttf"),
                Path.of("C:\\Windows\\Fonts\\arial.ttf")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    return new FontSelection(PDType0Font.load(document, candidate.toFile()), true);
                } catch (IOException ignored) {
                    // 当前字体不可用时继续尝试其他已安装字体。
                }
            }
        }
        return new FontSelection(new PDType1Font(Standard14Fonts.FontName.HELVETICA), false);
    }

    private List<String> wrapLines(String text, PDFont font, boolean unicode) throws IOException {
        List<String> lines = new ArrayList<>();
        float maxWidth = PDRectangle.A4.getWidth() - MARGIN * 2;
        for (String paragraph : text.split("\\R", -1)) {
            String normalized = paragraph.strip();
            if (normalized.isBlank()) {
                lines.add(" ");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (textWidth(font, unicode ? candidate : winAnsiSafe(candidate)) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private float textWidth(PDFont font, String value) throws IOException {
        return font.getStringWidth(value) / 1000f * FONT_SIZE;
    }

    private byte[] toDocx(ReportResponse report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            addZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            addZipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "word/document.xml", documentXml(report));
            zip.finish();
            return out.toByteArray();
        }
    }

    private String documentXml(ReportResponse report) {
        StringBuilder body = new StringBuilder();
        for (String line : report.content().split("\\R", -1)) {
            String style = paragraphStyle(line);
            String text = stripMarkdownPrefix(line);
            body.append("<w:p>");
            if (style != null) {
                body.append("<w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr>");
            }
            body.append("<w:r><w:t xml:space=\"preserve\">")
                    .append(escapeXml(text))
                    .append("</w:t></w:r></w:p>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    %s
                    <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
                  </w:body>
                </w:document>
                """.formatted(body);
    }

    private void addZipEntry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "pdf";
        }
        String normalized = format.toLowerCase(Locale.ROOT).trim();
        if ("markdown".equals(normalized)) {
            return "md";
        }
        return normalized;
    }

    private String filename(ReportResponse report, String format) {
        return "report-%s-v%d.%s".formatted(safeFilename(report.threadId()), report.version(), format);
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private String markdownToPlainText(String markdown) {
        return markdown.lines()
                .map(this::stripMarkdownPrefix)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private String paragraphStyle(String line) {
        if (line.startsWith("# ")) {
            return "Heading1";
        }
        if (line.startsWith("## ")) {
            return "Heading2";
        }
        if (line.startsWith("### ")) {
            return "Heading3";
        }
        return null;
    }

    private String stripMarkdownPrefix(String line) {
        return line.replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("^[-*+]\\s+", "- ")
                .replace("**", "")
                .replace("__", "")
                .stripTrailing();
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String winAnsiSafe(String value) {
        StringBuilder safe = new StringBuilder();
        value.codePoints().forEach(codePoint -> safe.append(codePoint <= 255 ? Character.toString(codePoint) : "?"));
        return safe.toString();
    }

    private record FontSelection(PDFont font, boolean unicode) {
    }
}
