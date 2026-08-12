package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.dto.agent.ResearchRunTraceResponse;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.springframework.stereotype.Component;

/**
 * 按用户隔离读取 Research Agent 的动态执行轨迹。
 */
@Component
public class AgentTraceReader {
    private final ResearchTaskMapper taskMapper;
    private final AgentRuntimeMapper runtimeMapper;

    public AgentTraceReader(ResearchTaskMapper taskMapper, AgentRuntimeMapper runtimeMapper) {
        this.taskMapper = taskMapper;
        this.runtimeMapper = runtimeMapper;
    }

    /** 查询指定用户任务的 Planner 与工具调用轨迹。 */
    public ResearchRunTraceResponse get(long ownerId, long taskId) {
        TaskExecutionRecord task = taskMapper.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Research Agent 任务"));
        return new ResearchRunTraceResponse(
                taskId,
                task.status(),
                task.stage(),
                runtimeMapper.findTurns(taskId),
                runtimeMapper.findToolCalls(taskId)
        );
    }
}
