package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.runtime.LegacyStockReportTraceReader;
import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.service.StockReportService;
import org.springframework.stereotype.Service;

/**
 * 实现旧版股票报告反馈、回放和轨迹的兼容查询。
 */
@Service
public class StockReportServiceImpl implements StockReportService {
    private final FinancialSnapshotMapper snapshotMapper;
    private final LegacyStockReportTraceReader traceReader;

    public StockReportServiceImpl(
            FinancialSnapshotMapper snapshotMapper,
            LegacyStockReportTraceReader traceReader
    ) {
        this.snapshotMapper = snapshotMapper;
        this.traceReader = traceReader;
    }

    public void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request) {
        snapshotMapper.saveFeedback(ownerId, taskId, request);
    }

    public StockReportReplayResponse replay(long ownerId, long taskId) {
        return snapshotMapper.findReplay(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告回放快照"));
    }

    public StockReportTraceResponse trace(long ownerId, long taskId) {
        return traceReader.get(ownerId, taskId);
    }
}
