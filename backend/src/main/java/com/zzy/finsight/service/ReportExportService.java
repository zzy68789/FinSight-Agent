package com.zzy.finsight.service;

import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.report.ExportedReport;

/**
 * 定义报告导出业务。
 */
public interface ReportExportService {
    /** 只从已持久化的 PASS 报告派生指定格式，不调用模型也不更新报告状态。 */
    ExportedReport export(ReportResponse report, String format);
}
