package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.dto.agent.ResearchBudgetRequest;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBudgetGuardTest {

    @Test
    void clientBudgetCanOnlyReduceServerLimits() {
        AgentBudgetGuard guard = new AgentBudgetGuard(
                8, 12, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
        );
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion("分析主要风险");
        request.setResearchDepth("standard");
        request.setBudget(new ResearchBudgetRequest(99, 5, 999));

        AgentBudget budget = guard.resolve(request);

        assertThat(budget.maxTurns()).isEqualTo(8);
        assertThat(budget.maxToolCalls()).isEqualTo(5);
        assertThat(budget.timeout()).isEqualTo(Duration.ofSeconds(180));
    }
}
