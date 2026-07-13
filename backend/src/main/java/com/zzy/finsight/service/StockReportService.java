package com.zzy.finsight.service;

import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 定义股票报告生成、重试、回放和反馈业务。
 */
public interface StockReportService {
    /** 异步执行证券报告任务并推送进度。 */
    void run(long ownerId, StockReportRequest request, SseEmitter emitter);

    /** 保存用户提交的报告错误反馈。 */
    void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request);

    /** 查询任务对应的数据快照回放。 */
    StockReportReplayResponse replay(long ownerId, long taskId);

    /** 重试满足条件的失败任务。 */
    void retry(long ownerId, long taskId);

    /** 查询任务的可信度执行轨迹。 */
    StockReportTraceResponse trace(long ownerId, long taskId);
}
