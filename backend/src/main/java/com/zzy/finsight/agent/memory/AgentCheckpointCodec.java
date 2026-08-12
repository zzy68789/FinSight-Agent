package com.zzy.finsight.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.CheckpointRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 编解码可恢复的 AgentState 检查点。
 */
@Component
public class AgentCheckpointCodec {
    public static final String CURRENT_VERSION = "agent-state-v2-lite";
    private final ObjectMapper objectMapper;

    public AgentCheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 读取并按 state_version 迁移 Agent 状态，未知版本或损坏内容按 fail-closed 拒绝恢复。 */
    public Optional<AgentState> decode(CheckpointRecord record) {
        if (record == null || record.stateJson() == null || record.stateJson().isBlank()) {
            return Optional.empty();
        }
        try {
            String version = record.stateVersion() == null || record.stateVersion().isBlank()
                    ? "agent-state-v1" : record.stateVersion();
            AgentState state = switch (version) {
                case CURRENT_VERSION -> objectMapper.readValue(record.stateJson(), AgentCheckpointState.class)
                        .toAgentState();
                case "agent-state-v1" -> migrateV1(objectMapper.readValue(record.stateJson(), AgentState.class));
                default -> throw new IllegalStateException("UNSUPPORTED_AGENT_STATE_VERSION：" + version);
            };
            if (record.turnNo() > 0 && state.getTurnNo() != record.turnNo()) {
                throw new IllegalStateException("CHECKPOINT_TURN_MISMATCH：检查点轮次与状态内容不一致");
            }
            return Optional.of(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CORRUPTED_AGENT_CHECKPOINT：检查点 JSON 无法解析", exception);
        }
    }

    /** 将旧版完整状态迁移为当前运行骨架，重对象随后由 StateStore 重新加载。 */
    private AgentState migrateV1(AgentState legacy) {
        return AgentCheckpointState.from(legacy).toAgentState();
    }
}
