package com.zzy.finsight.service.impl;

import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.domain.ReusableReportRecord;
import com.zzy.finsight.service.ReportService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 实现同用户已发布报告的安全复用查询。
 */
@Service
public class ReportServiceImpl implements ReportService {
    private final ReportMapper reportMapper;

    public ReportServiceImpl(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public Optional<ReusableReportRecord> findReusable(long ownerId, String generationContextHash) {
        return reportMapper.findReusable(ownerId, generationContextHash);
    }
}
