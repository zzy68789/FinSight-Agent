package com.zzy.finsight.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.quality.QualityGateDecision;
import com.zzy.finsight.agent.quality.QualityGateFailureType;
import com.zzy.finsight.agent.quality.QualityGateIssue;
import com.zzy.finsight.agent.quality.QualityGateRoute;
import com.zzy.finsight.agent.quality.QualityGateSource;
import com.zzy.finsight.domain.CheckpointRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCheckpointCodecTest {

    @Test
    void migratesV1ToLightweightStateAndKeepsRecoveryDirective() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentCheckpointCodec codec = new AgentCheckpointCodec(objectMapper);
        AgentState state = new AgentState();
        ToolInvocation previous = new ToolInvocation(
                "search_public_evidence", Map.of("query", "原始查询")
        );
        state.setToolInvocationHistory(List.of(previous));
        state.beginEvidenceRecovery(evidenceRecoveryDecision());
        state.invalidateDerivedResultsAfterEvidenceChange();
        CheckpointRecord record = new CheckpointRecord(
                1L,
                "thread-1",
                11L,
                "AGENT_STATE",
                2,
                "context-hash",
                objectMapper.writeValueAsString(state),
                LocalDateTime.of(2026, 8, 12, 12, 0)
        );

        AgentState restored = codec.decode(record).orElseThrow();

        assertThat(restored.hasPendingEvidenceRecovery()).isTrue();
        assertThat(restored.getEvidenceRecoveryDirective().issueCodes())
                .containsExactly("EVIDENCE_INSUFFICIENT");
        assertThat(restored.getToolInvocationHistory()).isEmpty();
        assertThat(restored.getReexecutionAllowedTools()).contains(
                "calculate_financial_metrics", "assess_financial_risk", "check_evidence_coverage"
        );
    }

    @Test
    void decodesCurrentLightweightVersionAndRejectsUnknownVersion() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentCheckpointCodec codec = new AgentCheckpointCodec(objectMapper);
        AgentState state = new AgentState();
        state.setTaskId(11L);
        state.setThreadId("thread-1");
        state.setTurnNo(3);
        state.setContextHash("context-hash");
        String lightJson = objectMapper.writeValueAsString(AgentCheckpointState.from(state));
        CheckpointRecord current = new CheckpointRecord(
                2L, "thread-1", 11L, "AGENT_STATE", 3, "context-hash",
                AgentCheckpointCodec.CURRENT_VERSION, 3, lightJson, LocalDateTime.now()
        );

        assertThat(codec.decode(current).orElseThrow().getTurnNo()).isEqualTo(3);
        CheckpointRecord unknown = new CheckpointRecord(
                3L, "thread-1", 11L, "AGENT_STATE", 3, "context-hash",
                "agent-state-v99", 3, lightJson, LocalDateTime.now()
        );
        assertThatThrownBy(() -> codec.decode(unknown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNSUPPORTED_AGENT_STATE_VERSION");
    }

    private QualityGateDecision evidenceRecoveryDecision() {
        QualityGateIssue issue = new QualityGateIssue(
                QualityGateFailureType.EVIDENCE_INSUFFICIENT,
                QualityGateSource.CITATION_REVIEW,
                "EVIDENCE_INSUFFICIENT",
                "有效证据不足"
        );
        return new QualityGateDecision(
                "FAIL",
                QualityGateRoute.COLLECT_MORE_EVIDENCE,
                List.of(issue),
                "EVIDENCE_INSUFFICIENT：有效证据不足"
        );
    }
}
