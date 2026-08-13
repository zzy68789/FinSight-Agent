package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchRunCreatedResponse;
import com.zzy.finsight.dto.agent.ResearchRunTraceResponse;
import com.zzy.finsight.service.ResearchAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 提供受约束 Research Agent 的运行、重试和轨迹接口。
 */
@RestController
@RequestMapping("/api/research-runs")
public class ResearchAgentController {
    private final ResearchAgentService researchAgentService;
    private final UserContext userContext;

    public ResearchAgentController(ResearchAgentService researchAgentService, UserContext userContext) {
        this.researchAgentService = researchAgentService;
        this.userContext = userContext;
    }

    /** 幂等创建 Research Agent 任务，客户端随后通过事件端点订阅运行轨迹。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ResearchRunCreatedResponse>> create(
            @Valid @RequestBody ResearchRunRequest request,
            @RequestHeader("Idempotency-Key") String clientRequestId
    ) {
        ResearchRunCreatedResponse response = researchAgentService.create(
                userContext.currentUserId(), request, clientRequestId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    /** 重试指定的失败或证据不足 Agent 任务。 */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<Void> retry(@PathVariable long taskId) {
        researchAgentService.retry(userContext.currentUserId(), taskId);
        return ApiResponse.success(null);
    }

    /** 按 Last-Event-ID 或查询序号重放缺失事件，并继续订阅实时 Agent 事件。 */
    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable long taskId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(name = "afterSequence", defaultValue = "0") long afterSequence
    ) {
        return researchAgentService.subscribe(
                userContext.currentUserId(), taskId, Math.max(afterSequence, parseSequence(lastEventId))
        );
    }

    /** 查询指定 Agent 任务的 Planner 和工具调用轨迹。 */
    @GetMapping("/{taskId}/trace")
    public ApiResponse<ResearchRunTraceResponse> trace(@PathVariable long taskId) {
        return ApiResponse.success(researchAgentService.trace(userContext.currentUserId(), taskId));
    }

    private long parseSequence(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
