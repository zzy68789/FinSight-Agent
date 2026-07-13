package com.zzy.finsight.service;

import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface StockReportService {
    void run(long ownerId, StockReportRequest request, SseEmitter emitter);

    void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request);

    StockReportReplayResponse replay(long ownerId, long taskId);

    void retry(long ownerId, long taskId);

    StockReportTraceResponse trace(long ownerId, long taskId);
}
