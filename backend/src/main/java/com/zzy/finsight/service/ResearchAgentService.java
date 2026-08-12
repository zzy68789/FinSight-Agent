package com.zzy.finsight.service;

import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchRunTraceResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 定义 Research Agent 运行、重试和轨迹查询业务。
 */
public interface ResearchAgentService {
    /** 异步启动 Research Agent 并通过 SSE 推送动态事件。 */
    void run(long ownerId, ResearchRunRequest request, SseEmitter emitter);

    /** 重试当前用户拥有的失败或证据不足任务。 */
    void retry(long ownerId, long taskId);

    /** 查询 Planner 决策与工具调用轨迹。 */
    ResearchRunTraceResponse trace(long ownerId, long taskId);
}
