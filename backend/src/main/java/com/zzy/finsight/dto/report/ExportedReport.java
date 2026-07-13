package com.zzy.finsight.dto.report;

public record ExportedReport(
        String filename,
        String contentType,
        byte[] bytes
) {
}
