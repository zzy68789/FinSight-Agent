package com.zzy.finsight.service.impl;

import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.report.ExportedReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceImplTest {

    private final ReportExportServiceImpl reportExportService = new ReportExportServiceImpl();

    @Test
    void exportsMarkdownReportAsPdfDocxAndMarkdownBytes() {
        ReportResponse report = new ReportResponse(
                21L,
                1L,
                "thread-1",
                "# Agent Report\n\n## Summary\n\n- RAG pipeline works.",
                3,
                "PASS",
                "",
                LocalDateTime.of(2026, 6, 25, 10, 0),
                false,
                null
        );

        ExportedReport pdf = reportExportService.export(report, "pdf");
        ExportedReport docx = reportExportService.export(report, "docx");
        ExportedReport markdown = reportExportService.export(report, "md");

        assertThat(pdf.filename()).isEqualTo("report-thread-1-v3.pdf");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(pdf.bytes()).startsWith("%PDF".getBytes());

        assertThat(docx.filename()).isEqualTo("report-thread-1-v3.docx");
        assertThat(docx.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(docx.bytes()).startsWith("PK".getBytes());

        assertThat(markdown.filename()).isEqualTo("report-thread-1-v3.md");
        assertThat(markdown.contentType()).isEqualTo("text/markdown;charset=UTF-8");
        assertThat(new String(markdown.bytes())).contains("# Agent Report");
    }
}
