package com.zzy.finsight.service;

import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;

/**
 * 定义旧版股票报告的反馈、回放和轨迹兼容查询业务。
 *
 * <p>新建和重试任务统一由 {@link ResearchAgentService} 执行，本接口不再承载固定工作流。</p>
 */
public interface StockReportService {
    /** 保存用户提交的报告错误反馈。 */
    void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request);

    /** 查询任务对应的数据快照回放。 */
    StockReportReplayResponse replay(long ownerId, long taskId);

    /** 查询任务的可信度执行轨迹。 */
    StockReportTraceResponse trace(long ownerId, long taskId);
}
