package com.zzy.drai.agent.graph;

import com.zzy.drai.agent.state.ResearchState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchRoutePolicyTest {

    @Test
    void stopsAfterResearchWhenDocumentModeFindsNoRelevantContext() {
        ResearchRoutePolicy policy = new ResearchRoutePolicy();

        String route = policy.afterResearch(new ResearchState(Map.of(
                ResearchState.SHOULD_STOP, true
        )));

        assertThat(route).isEqualTo("end");
    }

    @Test
    void loopsBackToPlannerWhenReviewFailsBeforeMaxRevision() {
        ResearchRoutePolicy policy = new ResearchRoutePolicy();

        String route = policy.afterReview(new ResearchState(Map.of(
                ResearchState.REVIEW_STATUS, "FAIL",
                ResearchState.REVISION_NUMBER, 1
        )));

        assertThat(route).isEqualTo("planner");
    }

    @Test
    void endsWhenReviewFailsAtMaxRevision() {
        ResearchRoutePolicy policy = new ResearchRoutePolicy();

        String route = policy.afterReview(new ResearchState(Map.of(
                ResearchState.REVIEW_STATUS, "FAIL",
                ResearchState.REVISION_NUMBER, 3
        )));

        assertThat(route).isEqualTo("end");
    }
}
