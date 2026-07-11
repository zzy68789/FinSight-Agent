package com.zzy.finsight.service;

public record ExportedReport(
        String filename,
        String contentType,
        byte[] bytes
) {
}
