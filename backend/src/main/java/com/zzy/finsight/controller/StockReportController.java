package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.service.StockReportService;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 提供股票报告生成、重试、回放和反馈接口。
 */
@RestController
@RequestMapping("/api")
public class StockReportController {
    private final StockReportService stockReportService;
    private final UserContext userContext;

    public StockReportController(StockReportService stockReportService, UserContext userContext) {
        this.stockReportService = stockReportService;
        this.userContext = userContext;
    }

    /** 创建股票投研任务并通过 SSE 推送执行进度。 */
    @PostMapping("/stock-reports")
    public SseEmitter createStockReport(@Valid @RequestBody StockReportRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        stockReportService.run(userContext.currentUserId(), request, emitter);
        return emitter;
    }

    /** 保存指定任务的报告问题反馈。 */
    @PostMapping("/stock-reports/{taskId}/feedback")
    public ApiResponse<Void> feedback(
            @PathVariable long taskId,
            @Valid @RequestBody StockBadCaseFeedbackRequest request
    ) {
        stockReportService.saveFeedback(userContext.currentUserId(), taskId, request);
        return ApiResponse.success(null);
    }

    /** 查询指定任务的数据快照、证据和指标。 */
    @GetMapping("/stock-reports/{taskId}/replay")
    public ApiResponse<StockReportReplayResponse> replay(@PathVariable long taskId) {
        return ApiResponse.success(stockReportService.replay(userContext.currentUserId(), taskId));
    }

    /** 重试当前用户拥有的失败任务。 */
    @PostMapping("/stock-reports/{taskId}/retry")
    public ApiResponse<Void> retry(@PathVariable long taskId) {
        stockReportService.retry(userContext.currentUserId(), taskId);
        return ApiResponse.success(null);
    }

    /** 查询指定任务的完整执行追踪。 */
    @GetMapping("/stock-reports/{taskId}/trace")
    public ApiResponse<StockReportTraceResponse> trace(@PathVariable long taskId) {
        return ApiResponse.success(stockReportService.trace(userContext.currentUserId(), taskId));
    }
}
