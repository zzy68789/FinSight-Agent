package com.zzy.finsight.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.ResearchPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProgressFingerprintTest {

    @Test
    void countsOnlyConsecutiveCommittedTurnsWithSameSemanticState() {
        ProgressFingerprint fingerprint = new ProgressFingerprint(
                new ObjectMapper().findAndRegisterModules(), mock(FinancialReportFingerprinter.class)
        );
        AgentState state = new AgentState();
        state.setPlan(plan("问题一"));

        assertThat(fingerprint.update(state)).isZero();
        assertThat(fingerprint.update(state)).isEqualTo(1);
        state.setPlan(plan("问题二"));
        assertThat(fingerprint.update(state)).isZero();
        assertThat(state.getLastProgressFingerprint()).isNotBlank();
    }

    private ResearchPlan plan(String unresolved) {
        return new ResearchPlan(
                "核验盈利", List.of("盈利可验证"), List.of("财务证据"), List.of(),
                List.of(unresolved), "LLM", "v1"
        );
    }
}
