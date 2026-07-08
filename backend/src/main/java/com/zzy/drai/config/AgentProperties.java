package com.zzy.drai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通用调研 agent 链路的可调参数。从原先 ResearcherNode / RagService 里的硬编码字面量外置出来，
 * 使检索深度、相关度阈值和 ReAct 工具调用轮次上限无需改代码即可调整。
 */
@ConfigurationProperties(prefix = "drai.agent")
public class AgentProperties {

    /** 本地知识库检索的 topK。 */
    private int localTopK = 5;

    /** 联网搜索的 topK。 */
    private int webTopK = 3;

    /** 本地文档算作相关证据的最小 RAG 分数。 */
    private double localRelevanceThreshold = 0.2d;

    /** Researcher agent 的最大连续工具调用次数（ReAct 循环失控保护）。 */
    private int researcherMaxToolCalls = 5;

    /** Planner agent 的最大连续工具调用次数（ReAct 循环失控保护）。 */
    private int plannerMaxToolCalls = 3;

    public int getLocalTopK() {
        return localTopK;
    }

    public void setLocalTopK(int localTopK) {
        this.localTopK = localTopK;
    }

    public int getWebTopK() {
        return webTopK;
    }

    public void setWebTopK(int webTopK) {
        this.webTopK = webTopK;
    }

    public double getLocalRelevanceThreshold() {
        return localRelevanceThreshold;
    }

    public void setLocalRelevanceThreshold(double localRelevanceThreshold) {
        this.localRelevanceThreshold = localRelevanceThreshold;
    }

    public int getResearcherMaxToolCalls() {
        return researcherMaxToolCalls;
    }

    public void setResearcherMaxToolCalls(int researcherMaxToolCalls) {
        this.researcherMaxToolCalls = researcherMaxToolCalls;
    }

    public int getPlannerMaxToolCalls() {
        return plannerMaxToolCalls;
    }

    public void setPlannerMaxToolCalls(int plannerMaxToolCalls) {
        this.plannerMaxToolCalls = plannerMaxToolCalls;
    }
}
