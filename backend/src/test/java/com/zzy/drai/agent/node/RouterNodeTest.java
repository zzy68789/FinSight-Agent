package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.ResearchState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RouterNodeTest {

    @Test
    void routesToPlannerWhenThreadHasNoPreviousReport() {
        RouterNode router = new RouterNode(threadId -> Optional.empty());

        String route = router.route(new ResearchState(Map.of(
                ResearchState.THREAD_ID, "thread-1",
                ResearchState.QUERY, "研究 AI Agent 技术趋势"
        )));

        assertThat(route).isEqualTo("planner");
    }

    @Test
    void routesToRefinerWhenPreviousReportExistsAndQueryLooksLikeRevision() {
        RouterNode router = new RouterNode(threadId -> Optional.of("# 旧报告"));

        String route = router.route(new ResearchState(Map.of(
                ResearchState.THREAD_ID, "thread-1",
                ResearchState.QUERY, "把第一部分扩写得更通俗"
        )));

        assertThat(route).isEqualTo("refiner");
    }
}
