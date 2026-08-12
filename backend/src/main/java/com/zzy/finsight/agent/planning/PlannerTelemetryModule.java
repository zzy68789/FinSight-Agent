package com.zzy.finsight.agent.planning;

import com.zzy.finsight.dto.agent.PlannerPerformanceSummary;
import com.zzy.finsight.mapper.AgentPlannerCallMapper;
import org.springframework.stereotype.Component;

/**
 * 集中保存并汇总 Planner 模型、结构、路由、Token 和延迟基线。
 */
@Component
public class PlannerTelemetryModule {
    private final AgentPlannerCallMapper mapper;

    public PlannerTelemetryModule(AgentPlannerCallMapper mapper) {
        this.mapper = mapper;
    }

    /** 保存一次 Planner 决策及确定性路由判定。 */
    public void record(long taskId, PlannerOutput<?> output, boolean routeCorrect) {
        mapper.save(taskId, output, routeCorrect);
    }

    /** 汇总指定任务的 Planner 性能基线。 */
    public PlannerPerformanceSummary summarize(long taskId) {
        return PlannerPerformanceSummary.from(mapper.findByTaskId(taskId));
    }
}
