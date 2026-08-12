package com.zzy.finsight.agent.memory;

import java.util.List;
import java.util.Set;

/**
 * 表示 Agent 检查点中的轻量运行引用，不复制证据、快照、报告和审查大对象。
 *
 * @param taskId 任务标识。
 * @param threadId 会话线程标识。
 * @param phase 当前阶段。
 * @param turnNo 已提交轮次。
 * @param toolCallCount 已消耗工具调用数。
 * @param replanCount 已重新规划次数。
 * @param reportRewriteCount 已重写报告次数。
 * @param consecutiveNoNewEvidenceTurns 连续无新增有效证据轮次。
 * @param snapshotId 金融快照标识。
 * @param evidenceRecoveryCount 补证据次数。
 * @param evidenceRecoveryDirective 当前补证据指令。
 * @param reexecutionAllowedTools 允许受控重算的工具名称。
 * @param observations 最近的短观察。
 * @param lastReviewReason 最近门禁说明。
 * @param stopReason 停止原因。
 * @param contextHash 请求上下文摘要。
 * @param plannerDegraded Planner 是否发生降级。
 */
public record AgentCheckpointState(
        long taskId,
        String threadId,
        String phase,
        int turnNo,
        int toolCallCount,
        int replanCount,
        int reportRewriteCount,
        int consecutiveNoNewEvidenceTurns,
        Long snapshotId,
        int evidenceRecoveryCount,
        EvidenceRecoveryDirective evidenceRecoveryDirective,
        Set<String> reexecutionAllowedTools,
        List<String> observations,
        String lastReviewReason,
        String stopReason,
        String contextHash,
        boolean plannerDegraded
) {
    public AgentCheckpointState {
        threadId = safe(threadId);
        phase = safe(phase);
        reexecutionAllowedTools = reexecutionAllowedTools == null ? Set.of() : Set.copyOf(reexecutionAllowedTools);
        observations = observations == null ? List.of() : observations.stream().skip(Math.max(0, observations.size() - 12L)).toList();
        lastReviewReason = safe(lastReviewReason);
        stopReason = safe(stopReason);
        contextHash = safe(contextHash);
    }

    /** 从完整内存状态提取轻量检查点。 */
    public static AgentCheckpointState from(AgentState state) {
        return new AgentCheckpointState(
                state.getTaskId(),
                state.getThreadId(),
                state.getPhase(),
                state.getTurnNo(),
                state.getToolCallCount(),
                state.getReplanCount(),
                state.getReportRewriteCount(),
                state.getConsecutiveNoNewEvidenceTurns(),
                state.getSnapshotId(),
                state.getEvidenceRecoveryCount(),
                state.getEvidenceRecoveryDirective(),
                state.getReexecutionAllowedTools(),
                state.getObservations(),
                state.getLastReviewReason(),
                state.getStopReason(),
                state.getContextHash(),
                state.isPlannerDegraded()
        );
    }

    /** 恢复不含大对象的内存状态骨架。 */
    public AgentState toAgentState() {
        AgentState state = new AgentState();
        state.setTaskId(taskId);
        state.setThreadId(threadId);
        state.setPhase(phase);
        state.setTurnNo(turnNo);
        state.setToolCallCount(toolCallCount);
        state.setReplanCount(replanCount);
        state.setReportRewriteCount(reportRewriteCount);
        state.setConsecutiveNoNewEvidenceTurns(consecutiveNoNewEvidenceTurns);
        state.setSnapshotId(snapshotId);
        state.setEvidenceRecoveryCount(evidenceRecoveryCount);
        state.setEvidenceRecoveryDirective(evidenceRecoveryDirective);
        state.setReexecutionAllowedTools(reexecutionAllowedTools);
        state.setObservations(observations);
        state.setLastReviewReason(lastReviewReason);
        state.setStopReason(stopReason);
        state.setContextHash(contextHash);
        state.setPlannerDegraded(plannerDegraded);
        return state;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
