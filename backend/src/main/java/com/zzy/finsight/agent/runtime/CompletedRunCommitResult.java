package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.event.AgentEvent;

import java.util.List;

/**
 * 表示最终报告、任务结束和事件完成原子提交后的结果。
 * @param reportId 新报告标识。
 * @param events 提交后可投递事件。
 */
public record CompletedRunCommitResult(long reportId, List<AgentEvent> events) {
    public CompletedRunCommitResult {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
