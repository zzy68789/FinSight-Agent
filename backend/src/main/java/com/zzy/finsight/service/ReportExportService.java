package com.zzy.finsight.service;

import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.report.ExportedReport;

/**
 * 定义报告导出业务。
 */
public interface ReportExportService {
    /** 按指定格式导出报告。 */
    ExportedReport export(ReportResponse report, String format);
}
