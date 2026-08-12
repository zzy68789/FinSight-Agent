package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.AgentStateStore;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 深化 Agent turn 的持久化边界，统一轮次、工具结果、证据、快照、指标、检查点与租约提交。
 */
@Component
public class DurableTurnCommitModule {
    private final AgentRuntimeMapper runtimeMapper;
    private final FinancialSnapshotMapper snapshotMapper;
    private final ResearchTaskMapper taskMapper;
    private final AgentStateStore stateStore;
    private final FinancialReportFingerprinter fingerprinter;

    public DurableTurnCommitModule(
            AgentRuntimeMapper runtimeMapper,
            FinancialSnapshotMapper snapshotMapper,
            ResearchTaskMapper taskMapper,
            AgentStateStore stateStore,
            FinancialReportFingerprinter fingerprinter
    ) {
        this.runtimeMapper = runtimeMapper;
        this.snapshotMapper = snapshotMapper;
        this.taskMapper = taskMapper;
        this.stateStore = stateStore;
        this.fingerprinter = fingerprinter;
    }

    /** 原子领取任务并返回带单调 epoch 的租约令牌。 */
    @Transactional
    public java.util.Optional<LeaseToken> claimLease(
            long taskId,
            String leaseOwner,
            LocalDateTime leaseUntil
    ) {
        if (taskMapper.acquireAttempt(taskId, leaseOwner, leaseUntil, LocalDateTime.now()) != 1) {
            return java.util.Optional.empty();
        }
        long epoch = taskMapper.findActiveLeaseEpoch(taskId, leaseOwner)
                .orElseThrow(() -> new IllegalStateException("LEASE_ACQUIRE_FAILED：领取后未找到租约代次"));
        return java.util.Optional.of(new LeaseToken(taskId, leaseOwner, epoch));
    }

    /** 幂等打开一轮 Planner 决策；恢复时复用同一 task + turnNo 记录。 */
    @Transactional
    public long openTurn(
            AgentState state,
            AgentAction action,
            int inputTokens,
            int outputTokens,
            long plannerDurationMs
    ) {
        return runtimeMapper.findTurn(state.getTaskId(), state.getTurnNo())
                .map(com.zzy.finsight.domain.AgentTurnRecord::id)
                .orElseGet(() -> runtimeMapper.saveTurn(
                        state.getTaskId(), state.getTurnNo(), state.getPhase(), action.type().name(), action,
                        "", "PLANNED", inputTokens, outputTokens, plannerDurationMs
                ));
    }

    /** 在调用外部工具前先写入可恢复的开始 journal。 */
    @Transactional
    public long journalToolStart(
            AgentState state,
            long turnId,
            String callId,
            String toolName,
            String argumentsHash,
            Object arguments,
            int attemptNo
    ) {
        return runtimeMapper.startToolCall(
                state.getTaskId(), turnId, callId, toolName, argumentsHash, arguments, attemptNo
        );
    }

    /** 原子提交完整 turn；任一写入或租约 fencing 失败都会回滚本轮全部数据库变化。 */
    @Transactional
    public void commitTurn(AgentTurnCommit commit, LeaseToken lease) {
        assertLease(commit.state(), lease);
        for (CommittedToolCall toolCall : commit.toolCalls()) {
            ToolResult result = toolCall.result();
            runtimeMapper.completeToolCall(
                    toolCall.databaseId(), result, result.status(), toolCall.durationMs(), result.errorCode(),
                    "FAILED".equals(result.status()) ? result.summary() : null, LocalDateTime.now()
            );
            persistToolEffects(
                    commit.ownerId(), commit.state(), toolCall.databaseId(), toolCall.invocation(), result
            );
        }
        if (runtimeMapper.completeTurn(
                commit.turnId(), commit.observation(), commit.status(), commit.durationMs()
        ) != 1) {
            throw new IllegalStateException("TURN_COMMIT_FAILED：轮次记录不存在或已被并发修改");
        }
        stateStore.save(commit.state());
        renew(commit.state(), lease);
    }

    /** 在首轮规划后原子保存轻量检查点并续租。 */
    @Transactional
    public void checkpointAndRenew(AgentState state, LeaseToken lease) {
        assertLease(state, lease);
        stateStore.save(state);
        renew(state, lease);
    }

    /** 原子写入最终轻量检查点，并仅由当前租约执行者结束任务。 */
    @Transactional
    public void finishTask(AgentState state, LeaseToken lease, String status, String reason, String error) {
        assertLease(state, lease);
        stateStore.save(state);
        if (!taskMapper.finishAgentFenced(lease, status, reason, error)) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得结束 Agent 任务");
        }
    }

    /** 在状态无法恢复时仅由当前租约执行者 fail-closed 结束任务。 */
    @Transactional
    public void failTask(LeaseToken lease, String reason, String error) {
        if (!taskMapper.finishAgentFenced(lease, "FAILED", reason, error)) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得标记 Agent 失败");
        }
    }

    /** 将已通过门禁的金融快照冻结，避免后续工具覆盖发布版本。 */
    @Transactional
    public void freezeSnapshot(AgentState state, String dataSnapshotHash) {
        if (state.getSnapshotId() == null || state.getSnapshot() == null) {
            throw new IllegalStateException("无法冻结尚未持久化的金融快照");
        }
        snapshotMapper.updateSnapshot(
                state.getSnapshotId(), state.getSnapshot(), dataSnapshotHash, "FROZEN", LocalDateTime.now()
        );
    }

    private void renew(AgentState state, LeaseToken lease) {
        if (!taskMapper.updateAgentProgressFenced(
                lease,
                state.getPhase(),
                state.getTurnNo(),
                state.getToolCallCount(),
                LocalDateTime.now().plusMinutes(5)
        )) {
            throw new IllegalStateException("LEASE_FENCED：任务租约已失效，拒绝提交 Agent turn");
        }
    }

    private void assertLease(AgentState state, LeaseToken lease) {
        if (lease == null || lease.taskId() != state.getTaskId()) {
            throw new IllegalArgumentException("租约令牌与 Agent 任务不匹配");
        }
    }

    private void persistToolEffects(
            long ownerId,
            AgentState state,
            long toolCallId,
            ToolInvocation invocation,
            ToolResult result
    ) {
        if ("FAILED".equals(result.status())) {
            return;
        }
        boolean snapshotCreated = false;
        if (state.getSubject() != null && state.getSnapshot() != null && state.getSnapshotId() == null) {
            String hash = fingerprinter.dataSnapshotHash(state.getSnapshot());
            long snapshotId = snapshotMapper.saveAgentSnapshot(
                    ownerId, state.getTaskId(), state.getThreadId(), state.getSnapshot(),
                    "COLLECTING", hash, toolCallId
            );
            state.setSnapshotId(snapshotId);
            snapshotCreated = true;
        }
        if (!snapshotCreated && state.getSnapshotId() != null && !result.evidenceItems().isEmpty()) {
            snapshotMapper.appendEvidence(
                    state.getSnapshotId(), state.getTaskId(), toolCallId, result.evidenceItems()
            );
        }
        if (state.getSnapshotId() != null && state.getSnapshot() != null) {
            String hash = fingerprinter.dataSnapshotHash(state.getSnapshot());
            snapshotMapper.synchronizeEvidenceIssues(
                    state.getSnapshotId(), state.getTaskId(), state.getSnapshot().evidenceItems()
            );
            snapshotMapper.updateSnapshot(
                    state.getSnapshotId(), state.getSnapshot(), hash, "COLLECTING", LocalDateTime.now()
            );
        }
        if (result.payload().containsKey("metrics") && state.getSnapshotId() != null) {
            snapshotMapper.replaceMetrics(state.getSnapshotId(), state.getTaskId(), state.getMetrics());
        }
    }
}
