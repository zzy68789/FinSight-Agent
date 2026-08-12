package com.zzy.finsight.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.agent.tool.ToolPayload;
import com.zzy.finsight.domain.AgentToolCallRecord;
import com.zzy.finsight.domain.AgentTurnRecord;
import com.zzy.finsight.domain.CheckpointRecord;
import com.zzy.finsight.domain.stock.PersistedFinancialSnapshot;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 负责轻量检查点版本迁移、重对象加载和工具 journal 对账恢复。
 */
@Component
public class AgentStateStore {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final CheckpointMapper checkpointMapper;
    private final FinancialSnapshotMapper snapshotMapper;
    private final AgentRuntimeMapper runtimeMapper;
    private final AgentCheckpointCodec checkpointCodec;
    private final ObjectMapper objectMapper;

    public AgentStateStore(
            CheckpointMapper checkpointMapper,
            FinancialSnapshotMapper snapshotMapper,
            AgentRuntimeMapper runtimeMapper,
            AgentCheckpointCodec checkpointCodec,
            ObjectMapper objectMapper
    ) {
        this.checkpointMapper = checkpointMapper;
        this.snapshotMapper = snapshotMapper;
        this.runtimeMapper = runtimeMapper;
        this.checkpointCodec = checkpointCodec;
        this.objectMapper = objectMapper;
    }

    /** 加载当前版本状态，并从数据库快照及工具 journal 重建未内嵌的大对象和调用索引。 */
    public Optional<AgentState> load(long ownerId, long taskId, String contextHash) {
        Optional<CheckpointRecord> checkpoint = checkpointMapper.findLatest(taskId, "AGENT_STATE", contextHash);
        if (checkpoint.isEmpty()) {
            return Optional.empty();
        }
        CheckpointRecord record = checkpoint.orElseThrow();
        AgentState state = checkpointCodec.decode(record)
                .orElseThrow(() -> new IllegalStateException("EMPTY_AGENT_CHECKPOINT：检查点不包含状态"));
        if (!contextHash.equals(state.getContextHash())) {
            throw new IllegalStateException("CHECKPOINT_CONTEXT_MISMATCH：请求上下文与检查点不一致");
        }
        hydrateSnapshot(ownerId, taskId, state);
        replayCommittedJournal(taskId, record.turnNo(), state);
        return Optional.of(state);
    }

    /** 只保存轻量状态引用，重对象继续由各自持久化表管理。 */
    public void save(AgentState state) {
        checkpointMapper.saveAgent(
                state.getThreadId(), state.getTaskId(), state.getTurnNo(), state.getContextHash(), state
        );
    }

    private void hydrateSnapshot(long ownerId, long taskId, AgentState state) {
        Optional<PersistedFinancialSnapshot> persisted = snapshotMapper.findSnapshot(ownerId, taskId);
        if (persisted.isEmpty()) {
            if (state.getSnapshotId() != null) {
                throw new IllegalStateException("CHECKPOINT_SNAPSHOT_MISSING：检查点引用的金融快照不存在");
            }
            return;
        }
        PersistedFinancialSnapshot snapshot = persisted.orElseThrow();
        if (state.getSnapshotId() != null && state.getSnapshotId() != snapshot.id()) {
            throw new IllegalStateException("CHECKPOINT_SNAPSHOT_MISMATCH：检查点引用的快照版本不一致");
        }
        state.setSnapshotId(snapshot.id());
        state.setSnapshot(snapshot.snapshot());
        state.setSubject(snapshot.snapshot().subject());
        state.setMetrics(snapshotMapper.findMetrics(taskId));
    }

    private void replayCommittedJournal(long taskId, int checkpointTurnNo, AgentState state) {
        Map<Long, Integer> turnNumbers = new HashMap<>();
        for (AgentTurnRecord turn : runtimeMapper.findTurns(taskId)) {
            turnNumbers.put(turn.id(), turn.turnNo());
        }
        Set<String> reexecutionAllowed = state.getReexecutionAllowedTools();
        for (AgentToolCallRecord call : runtimeMapper.findToolCalls(taskId)) {
            int turnNo = turnNumbers.getOrDefault(call.turnId(), Integer.MAX_VALUE);
            if (turnNo > checkpointTurnNo || "RUNNING".equals(call.status())) {
                continue;
            }
            ToolInvocation invocation = new ToolInvocation(call.toolName(), readArguments(call.argumentsJson()));
            ToolResult result = readResult(call.resultJson(), call);
            state.restoreToolJournalEntry(invocation, call.argumentsHash());
            applyDerivedResult(state, result);
        }
        state.setReexecutionAllowedTools(reexecutionAllowed);
    }

    private Map<String, Object> readArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("CORRUPTED_TOOL_JOURNAL：工具参数无法解析", exception);
        }
    }

    private ToolResult readResult(String json, AgentToolCallRecord call) {
        if (json == null || json.isBlank()) {
            return ToolResult.failure(
                    call.errorMessage() == null ? "工具执行未返回结果" : call.errorMessage(),
                    call.errorCode(),
                    false
            );
        }
        try {
            return objectMapper.readValue(json, ToolResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("CORRUPTED_TOOL_JOURNAL：工具结果无法解析", exception);
        }
    }

    private void applyDerivedResult(AgentState state, ToolResult result) {
        if (result.payload() instanceof ToolPayload.Risk risk) {
            state.setRiskAssessment(risk.riskAssessment());
        } else if (result.payload() instanceof ToolPayload.BullBear research) {
            state.setBullBearResearch(research.research());
        } else if (result.payload() instanceof ToolPayload.Metrics metrics) {
            state.setMetrics(metrics.metrics());
        }
    }
}
