package com.zzy.finsight.agent.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.quality.QualityGateDecision;
import com.zzy.finsight.agent.quality.QualityGateFailureType;
import com.zzy.finsight.agent.quality.QualityGateIssue;
import com.zzy.finsight.agent.quality.QualityGateRoute;
import com.zzy.finsight.agent.quality.QualityGateSource;
import com.zzy.finsight.agent.tool.ResearchTool;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.agent.tool.RawToolArguments;
import com.zzy.finsight.agent.tool.ToolContext;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchIntent;
import com.zzy.finsight.llm.LlmClient;
import com.zzy.finsight.llm.LlmGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchPlannerTest {

    @Test
    void fallbackPlanChangesEvidenceNeedsWithResearchQuestion() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());

        ResearchPlan financial = planner.createPlan(request("最近两个季度毛利率为何变化"), registry).value();
        ResearchPlan market = planner.createPlan(request("近期价格波动和估值发生了什么变化"), registry).value();

        assertThat(financial.plannerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(financial.requiredEvidence()).contains("财务报表及同比口径");
        assertThat(market.requiredEvidence()).contains("行情和估值快照");
    }

    @Test
    void fallbackPlanUsesResearchIntentWhenQuestionIsGeneric() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());
        ResearchRunRequest request = request("请完成本次研究");
        request.setResearchIntent(ResearchIntent.FINANCIAL_QUALITY);

        ResearchPlan plan = planner.createPlan(request, registry).value();

        assertThat(plan.requiredEvidence()).contains("财务报表、现金流及同比口径");
        assertThat(plan.hypotheses()).anyMatch(value -> value.contains("盈利质量"));
    }

    @Test
    void createPlanSendsTypedResearchIntentToModel() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LlmClient llm = new LlmClient() {
            @Override
            public String generate(String ignored, ModelType modelType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LlmGenerationResult generateWithMetadata(String value, ModelType modelType) {
                prompt.set(value);
                return new LlmGenerationResult(
                        "{\"goal\":\"核验事件影响\",\"hypotheses\":[\"公告影响待验证\"],"
                                + "\"requiredEvidence\":[\"公告与交叉来源\"],\"completedItems\":[],"
                                + "\"unresolvedQuestions\":[\"影响范围\"],\"plannerMode\":\"LLM\","
                                + "\"version\":\"research-planner-v4-intent-aware-routing\"}",
                        "smart-model", 20, 10, 30, "STOP", 8L
                );
            }
        };
        ResearchRunRequest request = request("核验近期公告影响");
        request.setResearchIntent(ResearchIntent.EVENT_IMPACT);

        new ResearchPlanner(llm, new ObjectMapper().findAndRegisterModules())
                .createPlan(request, new ResearchToolRegistry(List.of()));

        assertThat(prompt.get()).contains(
                "研究意图：EVENT_IMPACT",
                "重点核验截止日期前的公告、事件及其影响证据",
                "research_intent"
        );
    }

    @Test
    void fallbackActionSelectsQuestionSpecificToolInsteadOfFixedAllProviders() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());
        AgentState state = new AgentState();
        state.setRequest(request("分析近期价格波动和市场风险"));
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        state.setCompletedTools(java.util.Set.of("get_company_profile"));

        AgentAction action = planner.nextAction(state, registry).value();

        assertThat(action.type()).isEqualTo(AgentActionType.CALL_TOOL);
        assertThat(action.toolCalls()).extracting(ToolInvocation::toolName)
                .containsExactly("get_market_snapshot");
    }

    @Test
    void fallbackActionUsesQuestionFocusedPublicSearchAfterMarketSnapshot() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());
        AgentState state = new AgentState();
        state.setRequest(request("分析近期公告事件对经营的影响原因"));
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        state.setCompletedTools(java.util.Set.of(
                "get_company_profile", "get_financial_statements", "get_market_snapshot", "retrieve_uploaded_reports"
        ));

        AgentAction action = planner.nextAction(state, registry).value();

        assertThat(action.toolCalls()).extracting(ToolInvocation::toolName)
                .containsExactly("search_public_evidence");
    }

    @Test
    void fallbackEvidenceRecoveryChangesSearchQueryAfterPreviousSourceWasUsed() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(publicEvidenceTool()));
        AgentState state = new AgentState();
        state.setRequest(request("分析近期公告事件对经营的影响原因"));
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        state.setCompletedTools(Set.of(
                "get_company_profile",
                "get_financial_statements",
                "get_market_snapshot",
                "retrieve_uploaded_reports",
                "search_public_evidence"
        ));
        ToolInvocation previous = new ToolInvocation(
                "search_public_evidence", Map.of("query", "原始公告影响查询")
        );
        state.setToolInvocationHistory(List.of(previous));
        state.beginEvidenceRecovery(evidenceRecoveryDecision());

        PlannerOutput<AgentAction> output = planner.nextAction(state, registry);
        AgentAction action = output.value();

        assertThat(output.degraded()).isTrue();
        assertThat(action.type()).isEqualTo(AgentActionType.CALL_TOOL);
        assertThat(action.toolCalls()).hasSize(1);
        ToolInvocation recovery = action.toolCalls().get(0);
        assertThat(recovery.toolName()).isEqualTo("search_public_evidence");
        assertThat(recovery.arguments().get("query"))
                .isNotEqualTo(previous.arguments().get("query"))
                .asString()
                .contains("第1轮第1次");

        state.recordEvidenceRecoveryAttempt(recovery, 0L);
        state.setToolInvocationHistory(List.of(previous, recovery));
        ToolInvocation secondRecovery = planner.nextAction(state, registry).value().toolCalls().get(0);

        assertThat(secondRecovery.arguments().get("query"))
                .isNotEqualTo(recovery.arguments().get("query"))
                .asString()
                .contains("第1轮第2次");
    }

    @Test
    void replanSendsCommittedFeedbackAndRecoveryAttemptsDirectlyToSmartModel() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<LlmClient.ModelType> model = new AtomicReference<>();
        LlmClient llm = new LlmClient() {
            @Override
            public String generate(String ignored, ModelType modelType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LlmGenerationResult generateWithMetadata(String value, ModelType modelType) {
                prompt.set(value);
                model.set(modelType);
                return new LlmGenerationResult(
                        "{\"goal\":\"解决证据冲突\",\"hypotheses\":[\"公告口径可交叉验证\"],"
                                + "\"requiredEvidence\":[\"不同来源公告\"],\"completedItems\":[\"原始检索\"],"
                                + "\"unresolvedQuestions\":[\"冲突来源尚未核验\"],\"plannerMode\":\"LLM\","
                                + "\"version\":\"research-planner-v4-intent-aware-routing\"}",
                        "smart-model", 120, 40, 160, "STOP", 25L
                );
            }
        };
        ResearchPlanner planner = new ResearchPlanner(llm, new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(publicEvidenceTool()));
        AgentState state = new AgentState();
        state.setRequest(request("核验公告冲突"));
        state.setPlan(new ResearchPlan(
                "原计划", List.of("原假设"), List.of("原始公告"), List.of(), List.of("待核验"), "LLM", "v1"
        ));
        state.setObservations(List.of("search_public_evidence：发现两份口径冲突公告"));
        state.setLastReviewReason("EVIDENCE_CONFLICT：同一指标存在证据冲突");
        state.beginEvidenceRecovery(evidenceRecoveryDecision());
        state.recordEvidenceRecoveryAttempt(
                new ToolInvocation("search_public_evidence", Map.of("query", "更换来源核验公告")), 2L
        );

        PlannerOutput<ResearchPlan> output = planner.replan(state, registry);

        assertThat(output.degraded()).isFalse();
        assertThat(output.decisionType()).isEqualTo("REPLAN");
        assertThat(output.requestedModel()).isEqualTo("SMART");
        assertThat(output.actualModel()).isEqualTo("smart-model");
        assertThat(model.get()).isEqualTo(LlmClient.ModelType.SMART);
        assertThat(prompt.get()).contains(
                "发现两份口径冲突公告",
                "EVIDENCE_CONFLICT",
                "更换来源核验公告",
                "addedEffectiveEvidenceCount",
                "2"
        );
        assertThat(output.value().goal()).isEqualTo("解决证据冲突");
    }

    @Test
    void ordinaryNextActionUsesFastAndEscalatesToSmartAfterStructureFailure() {
        java.util.List<LlmClient.ModelType> models = new java.util.ArrayList<>();
        LlmClient llm = new LlmClient() {
            @Override
            public String generate(String ignored, ModelType modelType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LlmGenerationResult generateWithMetadata(String prompt, ModelType modelType) {
                models.add(modelType);
                if (models.size() == 1) {
                    return new LlmGenerationResult("not-json", "fast-model", 10, 2, 12, "STOP", 5L);
                }
                return new LlmGenerationResult(
                        "{\"type\":\"STOP_INSUFFICIENT_EVIDENCE\",\"toolCalls\":[],\"reason\":\"来源耗尽\"}",
                        "smart-model", 12, 4, 16, "STOP", 8L
                );
            }
        };
        ResearchPlanner planner = new ResearchPlanner(llm, new ObjectMapper().findAndRegisterModules());
        AgentState state = new AgentState();
        state.setRequest(request("分析盈利质量"));

        PlannerOutput<AgentAction> output = planner.nextAction(state, new ResearchToolRegistry(List.of()));

        assertThat(models).containsExactly(LlmClient.ModelType.FAST, LlmClient.ModelType.SMART);
        assertThat(output.requestedModel()).isEqualTo("SMART");
        assertThat(output.actualModel()).isEqualTo("smart-model");
        assertThat(output.structureAttempts()).isEqualTo(2);
        assertThat(output.inputTokens()).isEqualTo(22);
        assertThat(output.outputTokens()).isEqualTo(6);
    }

    @Test
    void failedStructuredCallsStillExposeCostMetadataBeforeFallback() {
        LlmClient llm = new LlmClient() {
            @Override
            public String generate(String ignored, ModelType modelType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LlmGenerationResult generateWithMetadata(String prompt, ModelType modelType) {
                String actual = modelType == ModelType.FAST ? "fast-model" : "smart-model";
                return new LlmGenerationResult("not-json", actual, 10, 3, 13, "STOP", 7L);
            }
        };
        ResearchPlanner planner = new ResearchPlanner(llm, new ObjectMapper().findAndRegisterModules());
        AgentState state = new AgentState();
        state.setRequest(request("分析盈利质量"));

        PlannerOutput<AgentAction> output = planner.nextAction(state, new ResearchToolRegistry(List.of()));

        assertThat(output.degraded()).isTrue();
        assertThat(output.structuredValid()).isFalse();
        assertThat(output.requestedModel()).isEqualTo("SMART");
        assertThat(output.actualModel()).isEqualTo("smart-model");
        assertThat(output.structureAttempts()).isEqualTo(2);
        assertThat(output.inputTokens()).isEqualTo(20);
        assertThat(output.outputTokens()).isEqualTo(6);
        assertThat(output.durationMs()).isEqualTo(14L);
    }

    private ResearchRunRequest request(String question) {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion(question);
        request.setSearchMode("hybrid");
        return request;
    }

    private LlmClient unavailableLlm() {
        return (prompt, modelType) -> {
            throw new IllegalStateException("LLM is not configured");
        };
    }

    private ResearchTool<?> publicEvidenceTool() {
        return new ResearchTool<RawToolArguments>() {
            @Override
            public String name() {
                return "search_public_evidence";
            }

            @Override
            public String description() {
                return "测试公开证据检索";
            }

            @Override
            public Set<String> allowedArguments() {
                return Set.of("query");
            }

            @Override
            public boolean producesEvidence() {
                return true;
            }

            @Override
            public ToolResult execute(ToolContext context, RawToolArguments arguments) {
                return ToolResult.success("完成");
            }
        };
    }

    private QualityGateDecision evidenceRecoveryDecision() {
        QualityGateIssue issue = new QualityGateIssue(
                QualityGateFailureType.EVIDENCE_INVALID,
                QualityGateSource.CITATION_REVIEW,
                "EVIDENCE_CONFLICT",
                "同一指标存在证据冲突"
        );
        return new QualityGateDecision(
                "FAIL",
                QualityGateRoute.COLLECT_MORE_EVIDENCE,
                List.of(issue),
                "EVIDENCE_CONFLICT：同一指标存在证据冲突"
        );
    }
}
