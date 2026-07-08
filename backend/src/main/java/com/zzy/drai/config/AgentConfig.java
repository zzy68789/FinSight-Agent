package com.zzy.drai.config;

import com.zzy.drai.agent.node.RouterNode;
import com.zzy.drai.agent.tool.PlannerAgent;
import com.zzy.drai.agent.tool.ResearchTools;
import com.zzy.drai.agent.tool.ResearcherAgent;
import com.zzy.drai.service.ReportService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration
public class AgentConfig {

    @Bean
    RouterNode routerNode(ReportService reportService) {
        return new RouterNode(reportService::findLatestByThread);
    }

    /**
     * ReAct 调研 agent。未配置 LLM 时返回 null，使节点回退到确定性检索路径。
     */
    @Bean
    @Nullable
    ResearcherAgent researcherAgent(
            @Nullable @Qualifier("smartChatModel") ChatModel smartChatModel,
            ResearchTools researchTools,
            AgentProperties properties
    ) {
        if (smartChatModel == null) {
            return null;
        }
        return AiServices.builder(ResearcherAgent.class)
                .chatModel(smartChatModel)
                .tools(researchTools)
                .maxSequentialToolsInvocations(properties.getResearcherMaxToolCalls())
                .build();
    }

    /**
     * ReAct 规划 agent。未配置 LLM 时返回 null，使节点回退到确定性 plan parser。
     */
    @Bean
    @Nullable
    PlannerAgent plannerAgent(
            @Nullable @Qualifier("fastChatModel") ChatModel fastChatModel,
            ResearchTools researchTools,
            AgentProperties properties
    ) {
        if (fastChatModel == null) {
            return null;
        }
        return AiServices.builder(PlannerAgent.class)
                .chatModel(fastChatModel)
                .tools(researchTools)
                .maxSequentialToolsInvocations(properties.getPlannerMaxToolCalls())
                .build();
    }
}
