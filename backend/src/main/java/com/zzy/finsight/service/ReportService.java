package com.zzy.finsight.service;

import com.zzy.finsight.domain.ReusableReportRecord;

import java.util.Optional;

/**
 * 定义已发布报告的安全复用查询。
 */
public interface ReportService {
    /** 查询同用户下可安全复用的报告。 */
    Optional<ReusableReportRecord> findReusable(long ownerId, String generationContextHash);
}
