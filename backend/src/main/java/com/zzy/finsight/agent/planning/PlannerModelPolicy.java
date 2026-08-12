package com.zzy.finsight.agent.planning;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.llm.LlmClient;

/**
 * 集中决定 Planner 不同决策使用的模型档位及结构失败后的升级策略。
 */
public final class PlannerModelPolicy {
    /** 根据决策复杂度选择首选模型。 */
    public LlmClient.ModelType select(String decisionType, AgentState state) {
        if (!"NEXT_ACTION".equals(decisionType)) {
            return LlmClient.ModelType.SMART;
        }
        if (state != null && (state.hasPendingEvidenceRecovery() || !state.getLastReviewReason().isBlank())) {
            return LlmClient.ModelType.SMART;
        }
        return LlmClient.ModelType.FAST;
    }

    /** FAST 结构失败时升级 SMART，SMART 失败时保持原档位重试。 */
    public LlmClient.ModelType retryModel(LlmClient.ModelType initial) {
        return initial == LlmClient.ModelType.FAST ? LlmClient.ModelType.SMART : initial;
    }
}
