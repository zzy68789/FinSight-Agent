package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.event.AgentEventDraft;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.AgentStateStore;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.agent.tool.ToolPayload;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final AgentEventOutboxMapper eventOutboxMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final ReportMapper reportMapper;

    public DurableTurnCommitModule(
            AgentRuntimeMapper runtimeMapper,
            FinancialSnapshotMapper snapshotMapper,
            ResearchTaskMapper taskMapper,
            AgentStateStore stateStore,
            FinancialReportFingerprinter fingerprinter,
            AgentEventOutboxMapper eventOutboxMapper,
            AgentStepLogMapper stepLogMapper,
            ReportMapper reportMapper
    ) {
        this.runtimeMapper = runtimeMapper;
        this.snapshotMapper = snapshotMapper;
        this.taskMapper = taskMapper;
        this.stateStore = stateStore;
        this.fingerprinter = fingerprinter;
        this.eventOutboxMapper = eventOutboxMapper;
        this.stepLogMapper = stepLogMapper;
        this.reportMapper = reportMapper;
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
            LeaseToken lease,
            AgentAction action,
            int inputTokens,
            int outputTokens,
            long plannerDurationMs
    ) {
        assertActiveLease(state, lease);
        return runtimeMapper.findTurn(state.getTaskId(), state.getTurnNo())
                .map(turn -> {
                    if (!turn.actionType().equals(action.type().name())) {
                        throw new IllegalStateException(
                                "TURN_ACTION_MISMATCH：恢复轮次动作与已持久化动作不一致"
                        );
                    }
                    return turn.id();
                })
                .orElseGet(() -> runtimeMapper.saveTurnFenced(
                        lease, state.getTurnNo(), state.getPhase(), action.type().name(), action,
                        inputTokens, outputTokens, plannerDurationMs
                ));
    }

    /** 在调用外部工具前先写入可恢复的开始 journal。 */
    @Transactional
    public long journalToolStart(
            AgentState state,
            LeaseToken lease,
            long turnId,
            String callId,
            String toolName,
            String argumentsHash,
            Object arguments,
            int attemptNo
    ) {
        assertActiveLease(state, lease);
        return runtimeMapper.startToolCallFenced(
                lease, turnId, callId, toolName, argumentsHash, arguments, attemptNo
        );
    }

    /** 原子提交完整 turn；任一写入或租约 fencing 失败都会回滚本轮全部数据库变化。 */
    @Transactional
    public List<AgentEvent> commitTurn(AgentTurnCommit commit, LeaseToken lease) {
        assertActiveLease(commit.state(), lease);
        persistTurn(commit);
        stateStore.save(commit.state());
        List<AgentEvent> events = commit.events().stream()
                .map(draft -> persistEvent(commit.state(), lease, draft))
                .toList();
        renew(commit.state(), lease);
        return events;
    }

    /** 原子提交最终 turn、PASS 报告、冻结快照、完成任务和 run_completed outbox。 */
    @Transactional
    public CompletedRunCommitResult commitCompletedRun(
            AgentTurnCommit commit,
            LeaseToken lease,
            FinalReportCommit report,
            AgentEventDraft completedEvent
    ) {
        assertActiveLease(commit.state(), lease);
        persistTurn(commit);
        long reportId = reportMapper.save(
                commit.ownerId(),
                commit.state().getTaskId(),
                commit.state().getThreadId(),
                report.content(),
                "PASS",
                report.critique(),
                commit.state().getSnapshotId(),
                report.dataSnapshotHash(),
                report.generationContextHash(),
                report.reusedFromReportId()
        );
        freezeSnapshotInternal(commit.state(), report.dataSnapshotHash());
        stateStore.save(commit.state());
        List<AgentEvent> events = new java.util.ArrayList<>();
        commit.events().stream()
                .map(draft -> persistEvent(commit.state(), lease, draft))
                .forEach(events::add);
        Map<String, Object> payload = new java.util.LinkedHashMap<>(completedEvent.payload());
        payload.put("reportId", reportId);
        payload.put("finalReport", report.content());
        events.add(persistEvent(
                commit.state(),
                lease,
                new AgentEventDraft(
                        completedEvent.type(), payload, completedEvent.durationMs(),
                        completedEvent.status(), completedEvent.errorMessage()
                )
        ));
        if (!taskMapper.finishAgentFenced(lease, "COMPLETED", "COMPLETED", null)) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得结束 Agent 任务");
        }
        return new CompletedRunCommitResult(reportId, events);
    }

    private void persistTurn(AgentTurnCommit commit) {
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
    }

    /** 在首轮规划后原子保存轻量检查点并续租。 */
    @Transactional
    public void checkpointAndRenew(AgentState state, LeaseToken lease) {
        assertActiveLease(state, lease);
        stateStore.save(state);
        renew(state, lease);
    }

    /** 原子写入最终轻量检查点，并仅由当前租约执行者结束任务。 */
    @Transactional
    public void finishTask(AgentState state, LeaseToken lease, String status, String reason, String error) {
        assertActiveLease(state, lease);
        stateStore.save(state);
        if (!taskMapper.finishAgentFenced(lease, status, reason, error)) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得结束 Agent 任务");
        }
    }

    /** 原子保存最终状态、终止事件 outbox，并以当前租约结束非成功任务。 */
    @Transactional
    public AgentEvent finishTaskWithEvent(
            AgentState state,
            LeaseToken lease,
            String status,
            String reason,
            String error,
            AgentEventDraft event
    ) {
        assertActiveLease(state, lease);
        stateStore.save(state);
        AgentEvent persisted = persistEvent(state, lease, event);
        if (!taskMapper.finishAgentFenced(lease, status, reason, error)) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得结束 Agent 任务");
        }
        return persisted;
    }

    /** 在状态无法恢复时仅由当前租约执行者 fail-closed 结束任务。 */
    @Transactional
    public void failTask(LeaseToken lease, String reason, String error) {
        if (!taskMapper.finishAgentFenced(lease, "FAILED", reason, error)) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得标记 Agent 失败");
        }
    }

    /** 在独立事务中以当前租约写入版本化事件 outbox 和兼容步骤日志。 */
    @Transactional
    public AgentEvent appendEvent(AgentState state, LeaseToken lease, AgentEventDraft draft) {
        assertActiveLease(state, lease);
        return persistEvent(state, lease, draft);
    }

    /** 将已通过门禁的金融快照冻结，避免后续工具覆盖发布版本。 */
    @Transactional
    public void freezeSnapshot(AgentState state, String dataSnapshotHash) {
        freezeSnapshotInternal(state, dataSnapshotHash);
    }

    private void freezeSnapshotInternal(AgentState state, String dataSnapshotHash) {
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

    private void assertActiveLease(AgentState state, LeaseToken lease) {
        if (lease == null || lease.taskId() != state.getTaskId()) {
            throw new IllegalArgumentException("租约令牌与 Agent 任务不匹配");
        }
        long activeEpoch = taskMapper.findActiveLeaseEpoch(lease.taskId(), lease.owner())
                .orElseThrow(() -> new IllegalStateException("LEASE_FENCED：任务租约已失效"));
        if (activeEpoch != lease.epoch()) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得写入 Agent journal");
        }
    }

    private AgentEvent persistEvent(AgentState state, LeaseToken lease, AgentEventDraft draft) {
        long sequence = taskMapper.nextEventSequence(lease);
        LocalDateTime createdAt = LocalDateTime.now();
        AgentEvent pending = new AgentEvent(
                0L,
                AgentEvent.CURRENT_SCHEMA_VERSION,
                state.getTaskId(),
                state.getThreadId(),
                sequence,
                java.util.UUID.randomUUID().toString(),
                state.getTurnNo(),
                draft.type(),
                draft.status(),
                draft.payload(),
                draft.errorMessage(),
                draft.durationMs(),
                null,
                createdAt
        );
        long id = eventOutboxMapper.insert(pending);
        stepLogMapper.save(
                state.getTaskId(), draft.type(), pending.ssePayload(), Math.max(1, state.getTurnNo()),
                draft.durationMs(), draft.status(), draft.errorMessage()
        );
        return new AgentEvent(
                id,
                pending.schemaVersion(),
                pending.taskId(),
                pending.threadId(),
                pending.sequence(),
                pending.eventId(),
                pending.turnNo(),
                pending.type(),
                pending.status(),
                pending.payload(),
                pending.errorMessage(),
                pending.durationMs(),
                null,
                pending.createdAt()
        );
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
        if (result.payload() instanceof ToolPayload.Metrics && state.getSnapshotId() != null) {
            snapshotMapper.replaceMetrics(state.getSnapshotId(), state.getTaskId(), state.getMetrics());
        }
    }
}
