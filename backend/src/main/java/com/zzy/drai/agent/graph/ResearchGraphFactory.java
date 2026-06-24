package com.zzy.drai.agent.graph;

import com.zzy.drai.agent.node.PlannerNode;
import com.zzy.drai.agent.node.RefinerNode;
import com.zzy.drai.agent.node.ResearcherNode;
import com.zzy.drai.agent.node.ReviewerNode;
import com.zzy.drai.agent.node.RouterNode;
import com.zzy.drai.agent.node.WriterNode;
import com.zzy.drai.agent.state.ResearchState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResearchGraphFactory {
    private final RouterNode routerNode;
    private final PlannerNode plannerNode;
    private final ResearcherNode researcherNode;
    private final WriterNode writerNode;
    private final ReviewerNode reviewerNode;
    private final RefinerNode refinerNode;
    private final ResearchRoutePolicy routePolicy;

    public ResearchGraphFactory(
            RouterNode routerNode,
            PlannerNode plannerNode,
            ResearcherNode researcherNode,
            WriterNode writerNode,
            ReviewerNode reviewerNode,
            RefinerNode refinerNode
    ) {
        this.routerNode = routerNode;
        this.plannerNode = plannerNode;
        this.researcherNode = researcherNode;
        this.writerNode = writerNode;
        this.reviewerNode = reviewerNode;
        this.refinerNode = refinerNode;
        this.routePolicy = new ResearchRoutePolicy();
    }

    public CompiledGraph<ResearchState> create() {
        try {
            StateGraph<ResearchState> graph = new StateGraph<>(ResearchState::new);
            graph.addNode("planner", AsyncNodeAction.node_async(plannerNode::apply));
            graph.addNode("researcher", AsyncNodeAction.node_async(researcherNode::apply));
            graph.addNode("writer", AsyncNodeAction.node_async(writerNode::apply));
            graph.addNode("reviewer", AsyncNodeAction.node_async(reviewerNode::apply));
            graph.addNode("refiner", AsyncNodeAction.node_async(refinerNode::apply));

            graph.addConditionalEdges(StateGraph.START, AsyncEdgeAction.edge_async(routerNode::route),
                    Map.of("planner", "planner", "refiner", "refiner"));
            graph.addEdge("planner", "researcher");
            graph.addConditionalEdges("researcher", AsyncEdgeAction.edge_async(routePolicy::afterResearch),
                    Map.of("writer", "writer", "end", StateGraph.END));
            graph.addEdge("writer", "reviewer");
            graph.addConditionalEdges("reviewer", AsyncEdgeAction.edge_async(routePolicy::afterReview),
                    Map.of("planner", "planner", "end", StateGraph.END));
            graph.addEdge("refiner", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("构建 LangGraph4j 工作流失败", e);
        }
    }
}
