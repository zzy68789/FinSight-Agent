package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.AgentSubTaskResult;
import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.agent.tool.PlannerAgent;
import com.zzy.drai.agent.tool.ToolCallLog;
import com.zzy.drai.llm.LlmClient;
import com.zzy.drai.llm.PlanParser;
import com.zzy.drai.llm.PromptTemplates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把研究问题规划成子任务。
 *
 * <p>首轮（此前没有 critique）且 {@link PlannerAgent} 可用时，agent 以 ReAct 循环先探查本地
 * 知识库再决定如何拆分问题。重试轮次（已存在 critique）时使用确定性的 critique-aware prompt，
 * 使 Reviewer 失败回环继续生效。两条路径的输出都经 {@link PlanParser} 做解析/兜底。
 */
@Component
public class PlannerNode {

    @Nullable
    private final PlannerAgent plannerAgent;
    private final LlmClient llmClient;
    private final PlanParser planParser;

    @Autowired
    public PlannerNode(@Nullable PlannerAgent plannerAgent, LlmClient llmClient, PlanParser planParser) {
        this.plannerAgent = plannerAgent;
        this.llmClient = llmClient;
        this.planParser = planParser;
    }

    public Map<String, Object> apply(ResearchState state) {
        String raw = generatePlan(state);
        List<String> plan = planParser.parse(raw, state.query());
        List<AgentSubTaskResult> subTasks = plan.stream()
                .map(query -> new AgentSubTaskResult(query, "PENDING", List.of(), 0, 0, ""))
                .toList();
        return Map.of(
                ResearchState.PLAN, plan,
                ResearchState.SUB_TASK_RESULTS, subTasks
        );
    }

    private String generatePlan(ResearchState state) {
        boolean firstPass = state.critique() == null || state.critique().isBlank();
        if (plannerAgent != null && firstPass) {
            ToolCallLog.begin();
            try {
                return plannerAgent.plan(state.query());
            } catch (RuntimeException e) {
                return deterministicPlan(state);
            } finally {
                ToolCallLog.clear();
            }
        }
        return deterministicPlan(state);
    }

    private String deterministicPlan(ResearchState state) {
        return llmClient.generate(PromptTemplates.planner(state.query(), state.critique()), LlmClient.ModelType.FAST);
    }
}
