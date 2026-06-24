package com.zzy.drai.agent.graph;

import com.zzy.drai.agent.state.ResearchState;

public class ResearchRoutePolicy {
    private static final int MAX_REVISIONS = 3;

    public String afterResearch(ResearchState state) {
        return state.shouldStop() ? "end" : "writer";
    }

    public String afterReview(ResearchState state) {
        if (state.revisionNumber() >= MAX_REVISIONS) {
            return "end";
        }
        return "FAIL".equalsIgnoreCase(state.reviewStatus()) ? "planner" : "end";
    }
}
