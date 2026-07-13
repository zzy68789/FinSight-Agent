package com.zzy.finsight.service;

import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.report.ExportedReport;

public interface ReportExportService {
    ExportedReport export(ReportResponse report, String format);
}
