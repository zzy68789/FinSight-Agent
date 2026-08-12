package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchRunTraceResponse;
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

    /** 创建 Research Agent 任务并通过 SSE 推送动态计划与工具事件。 */
    @PostMapping
    public SseEmitter create(@Valid @RequestBody ResearchRunRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        researchAgentService.run(userContext.currentUserId(), request, emitter);
        return emitter;
    }

    /** 重试指定的失败或证据不足 Agent 任务。 */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<Void> retry(@PathVariable long taskId) {
        researchAgentService.retry(userContext.currentUserId(), taskId);
        return ApiResponse.success(null);
    }

    /** 查询指定 Agent 任务的 Planner 和工具调用轨迹。 */
    @GetMapping("/{taskId}/trace")
    public ApiResponse<ResearchRunTraceResponse> trace(@PathVariable long taskId) {
        return ApiResponse.success(researchAgentService.trace(userContext.currentUserId(), taskId));
    }
}
