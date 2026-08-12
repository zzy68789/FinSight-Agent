package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.dto.agent.ResearchBudgetRequest;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 将客户端预算收紧到服务端允许范围并判断停止条件。
 */
@Component
public class AgentBudgetGuard {
    private final int serverMaxTurns;
    private final int serverMaxToolCalls;
    private final int maxReplans;
    private final int maxParallelTools;
    private final int maxReportRewrites;
    private final Duration serverTimeout;
    private final Duration toolTimeout;

    public AgentBudgetGuard(
            @Value("${finsight.agent.max-turns:8}") int serverMaxTurns,
            @Value("${finsight.agent.max-tool-calls:12}") int serverMaxToolCalls,
            @Value("${finsight.agent.max-replans:3}") int maxReplans,
            @Value("${finsight.agent.max-parallel-tools:4}") int maxParallelTools,
            @Value("${finsight.agent.max-report-rewrites:2}") int maxReportRewrites,
            @Value("${finsight.agent.timeout:PT180S}") Duration serverTimeout,
            @Value("${finsight.agent.tool-timeout:PT30S}") Duration toolTimeout
    ) {
        this.serverMaxTurns = Math.max(1, serverMaxTurns);
        this.serverMaxToolCalls = Math.max(1, serverMaxToolCalls);
        this.maxReplans = Math.max(0, maxReplans);
        this.maxParallelTools = Math.max(1, maxParallelTools);
        this.maxReportRewrites = Math.max(0, maxReportRewrites);
        this.serverTimeout = positive(serverTimeout, Duration.ofSeconds(180));
        this.toolTimeout = positive(toolTimeout, Duration.ofSeconds(30));
    }

    /** 计算本次请求最终生效的预算。 */
    public AgentBudget resolve(ResearchRunRequest request) {
        ResearchBudgetRequest client = request.getBudget();
        int depthTurns = "quick".equals(request.getResearchDepth()) ? Math.min(5, serverMaxTurns) : serverMaxTurns;
        int depthTools = "quick".equals(request.getResearchDepth()) ? Math.min(7, serverMaxToolCalls) : serverMaxToolCalls;
        int turns = clamp(client == null ? null : client.maxTurns(), depthTurns);
        int tools = clamp(client == null ? null : client.maxToolCalls(), depthTools);
        int timeoutSeconds = clamp(
                client == null ? null : client.timeoutSeconds(),
                Math.toIntExact(serverTimeout.toSeconds())
        );
        return new AgentBudget(
                turns,
                tools,
                maxReplans,
                maxParallelTools,
                maxReportRewrites,
                Duration.ofSeconds(timeoutSeconds),
                toolTimeout
        );
    }

    /** 判断当前状态是否已经耗尽轮次或工具预算。 */
    public boolean exhausted(com.zzy.finsight.agent.memory.AgentState state, AgentBudget budget) {
        return state.getTurnNo() >= budget.maxTurns()
                || state.getToolCallCount() >= budget.maxToolCalls();
    }

    private int clamp(Integer requested, int maximum) {
        if (requested == null || requested <= 0) {
            return maximum;
        }
        return Math.max(1, Math.min(requested, maximum));
    }

    private Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
