package com.zzy.finsight.dto.report;

/**
 * 表示已生成的报告导出文件。
 * @param filename 导出文件名。
 * @param contentType 文件内容类型。
 * @param bytes 文件二进制内容。
 */
public record ExportedReport(
        String filename,
        String contentType,
        byte[] bytes
) {
}
