package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.memory.AgentStateStore;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DurableAgentRunnerContextHashTest {

    @Test
    void comparisonScopeChangesCheckpointContextHash() {
        DurableAgentRunner runner = new DurableAgentRunner(
                mock(ResearchAgentRuntime.class),
                mock(AgentBudgetGuard.class),
                mock(ResearchTaskMapper.class),
                mock(AgentStateStore.class),
                mock(DurableTurnCommitModule.class),
                mock(AgentStepLogMapper.class),
                mock(TaskRuntimeStateService.class),
                mock(ResearchRunRequestCodec.class)
        );
        ResearchRunRequest first = request(List.of("000858.SZ"));
        ResearchRunRequest second = request(List.of("600809.SH"));

        assertThat(runner.requestContextHash(first))
                .hasSize(64)
                .isNotEqualTo(runner.requestContextHash(second));
    }

    private ResearchRunRequest request(List<String> comparisonTickers) {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519.SH");
        request.setResearchQuestion("比较主营业务质量与估值差异");
        request.setAsOfDate(LocalDate.of(2026, 8, 13));
        request.setComparisonTickers(comparisonTickers);
        return request;
    }
}
