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
    private final ObjectMapper objectMapper;

    public AgentCheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 尝试读取 Agent 状态，损坏内容返回空结果并由 Runtime 安全重建。 */
    public Optional<AgentState> decode(CheckpointRecord record) {
        if (record == null || record.stateJson() == null || record.stateJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(record.stateJson(), AgentState.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }
}
