package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.service.StockReportService;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.service.ResearchAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 提供旧版股票报告请求到 Research Agent 的兼容入口及历史数据查询接口。
 */
@RestController
@RequestMapping("/api")
public class StockReportController {
    private final StockReportService stockReportService;
    private final ResearchAgentService researchAgentService;
    private final UserContext userContext;

    public StockReportController(
            StockReportService stockReportService,
            ResearchAgentService researchAgentService,
            UserContext userContext
    ) {
        this.stockReportService = stockReportService;
        this.researchAgentService = researchAgentService;
        this.userContext = userContext;
    }

    /** 创建股票投研任务并通过 SSE 推送执行进度。 */
    @PostMapping("/stock-reports")
    public SseEmitter createStockReport(@Valid @RequestBody StockReportRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        researchAgentService.run(userContext.currentUserId(), adapt(request), emitter);
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
        researchAgentService.retry(userContext.currentUserId(), taskId);
        return ApiResponse.success(null);
    }

    /** 查询指定任务的完整执行追踪。 */
    @GetMapping("/stock-reports/{taskId}/trace")
    public ApiResponse<StockReportTraceResponse> trace(@PathVariable long taskId) {
        return ApiResponse.success(stockReportService.trace(userContext.currentUserId(), taskId));
    }

    /** 将旧版报告期请求转换为带默认研究问题的 Agent 请求。 */
    private ResearchRunRequest adapt(StockReportRequest request) {
        ResearchRunRequest adapted = new ResearchRunRequest();
        adapted.setTicker(request.getTicker());
        adapted.setThreadId(request.getThreadId());
        adapted.setSearchMode(request.getSearchMode());
        adapted.setResearchDepth("standard");
        String reportPeriod = request.getReportPeriod();
        if (reportPeriod.matches("\\d{8}")) {
            try {
                adapted.setAsOfDate(java.time.LocalDate.parse(
                        reportPeriod, java.time.format.DateTimeFormatter.BASIC_ISO_DATE
                ));
            } catch (java.time.DateTimeException ignored) {
                // 非法旧报告期保留为问题上下文，由 Agent 在证据中显式处理。
            }
        }
        adapted.setResearchQuestion(
                "截至 " + reportPeriod + "，围绕 " + request.getTicker().toUpperCase(java.util.Locale.ROOT)
                        + " 生成一份覆盖财务表现、估值观察、主要风险和后续观察点的证券研究报告"
        );
        return adapted;
    }
}
