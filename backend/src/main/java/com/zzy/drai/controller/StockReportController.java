package com.zzy.drai.controller;

import com.zzy.drai.auth.UserContext;
import com.zzy.drai.dto.ApiResponse;
import com.zzy.drai.financial.StockBadCaseFeedbackRequest;
import com.zzy.drai.financial.StockReportReplayResponse;
import com.zzy.drai.financial.StockReportRequest;
import com.zzy.drai.financial.StockReportService;
import com.zzy.drai.financial.StockReportTraceResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class StockReportController {
    private final StockReportService stockReportService;
    private final UserContext userContext;

    public StockReportController(StockReportService stockReportService, UserContext userContext) {
        this.stockReportService = stockReportService;
        this.userContext = userContext;
    }

    @PostMapping("/stock-reports")
    public SseEmitter createStockReport(@Valid @RequestBody StockReportRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        stockReportService.run(userContext.currentUserId(), request, emitter);
        return emitter;
    }

    @PostMapping("/stock-reports/{taskId}/feedback")
    public ApiResponse<Void> feedback(
            @PathVariable long taskId,
            @Valid @RequestBody StockBadCaseFeedbackRequest request
    ) {
        stockReportService.saveFeedback(userContext.currentUserId(), taskId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/stock-reports/{taskId}/replay")
    public ApiResponse<StockReportReplayResponse> replay(@PathVariable long taskId) {
        return ApiResponse.success(stockReportService.replay(userContext.currentUserId(), taskId));
    }

    @PostMapping("/stock-reports/{taskId}/retry")
    public ApiResponse<Void> retry(@PathVariable long taskId) {
        stockReportService.retry(userContext.currentUserId(), taskId);
        return ApiResponse.success(null);
    }

    @GetMapping("/stock-reports/{taskId}/trace")
    public ApiResponse<StockReportTraceResponse> trace(@PathVariable long taskId) {
        return ApiResponse.success(stockReportService.trace(userContext.currentUserId(), taskId));
    }
}
