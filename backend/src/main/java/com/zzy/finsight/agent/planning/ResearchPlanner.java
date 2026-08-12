package com.zzy.finsight.agent.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.llm.LlmClient;
import com.zzy.finsight.llm.LlmGenerationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 使用 LLM 生成研究计划和下一步动作，并在模型不可用时显式降级为规则决策。
 */
@Component
public class ResearchPlanner {
    public static final String PLANNER_VERSION = "research-planner-v2-evidence-recovery";
    private static final int MAX_STRUCTURE_ATTEMPTS = 2;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ResearchPlanner(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** 根据研究问题和工具目录生成首版计划。 */
    public PlannerOutput<ResearchPlan> createPlan(ResearchRunRequest request, ResearchToolRegistry registry) {
        String prompt = """
                你是受约束的 A股/ETF 投研 Planner。只规划研究，不给买卖、仓位或收益保证。
                根据研究问题和工具目录返回严格 JSON，不要 Markdown：
                {"goal":"...","hypotheses":["..."],"requiredEvidence":["..."],"completedItems":[],"unresolvedQuestions":["..."],"plannerMode":"LLM","version":"%s"}
                研究请求：%s
                可用工具：%s
                """.formatted(PLANNER_VERSION, json(request), json(registry.catalog()));
        try {
            LlmGenerationResult result = generateStructured(prompt, ResearchPlan.class);
            ResearchPlan plan = objectMapper.readValue(cleanJson(result.text()), ResearchPlan.class);
            validatePlan(plan);
            return new PlannerOutput<>(plan, false, "", result.inputTokens(), result.outputTokens(), result.durationMs());
        } catch (RuntimeException | JsonProcessingException exception) {
            return PlannerOutput.degraded(fallbackPlan(request), classify(exception));
        }
    }

    /** 根据当前观察和剩余问题选择下一步动作。 */
    public PlannerOutput<AgentAction> nextAction(AgentState state, ResearchToolRegistry registry) {
        String prompt = """
                你是受约束的 A股/ETF Research Agent。你只能从工具目录中选择动作，不能调用交易、下单、仓位或任意代码工具。
                每次只返回严格 JSON，不要 Markdown。格式：
                {"type":"CALL_TOOL|CALL_TOOLS_PARALLEL|REPLAN|SYNTHESIZE|STOP_INSUFFICIENT_EVIDENCE","toolCalls":[{"toolName":"工具名","arguments":{}}],"reason":"..."}
                规则：CALL_TOOL 必须且只能包含一个调用；并行调用只能选择 allowParallel=true 的独立工具；证据、指标和风险不足时不得 SYNTHESIZE；无法补证时停止。
                如果 evidenceRecovery.status=PENDING，下一步必须调用 producesEvidence=true 的工具，并且相对 toolInvocationHistory 更换数据源或 arguments；不得继续综合、计算指标或原样重复检索。
                当前状态：%s
                可用工具：%s
                """.formatted(json(stateSummary(state)), json(registry.catalog()));
        try {
            LlmGenerationResult result = generateStructured(prompt, AgentAction.class);
            AgentAction action = objectMapper.readValue(cleanJson(result.text()), AgentAction.class);
            validateActionShape(action);
            validateEvidenceRecoveryAction(action, state, registry);
            return new PlannerOutput<>(action, false, "", result.inputTokens(), result.outputTokens(), result.durationMs());
        } catch (RuntimeException | JsonProcessingException exception) {
            return PlannerOutput.degraded(fallbackAction(state, registry), classify(exception));
        }
    }

    /** 根据当前观察和审查反馈重建计划。 */
    public PlannerOutput<ResearchPlan> replan(AgentState state, ResearchToolRegistry registry) {
        PlannerOutput<ResearchPlan> output = createPlan(state.getRequest(), registry);
        ResearchPlan original = output.value();
        List<String> unresolved = new ArrayList<>(original.unresolvedQuestions());
        unresolved.addAll(state.getObservations().stream().skip(Math.max(0, state.getObservations().size() - 5)).toList());
        if (!state.getLastReviewReason().isBlank()) {
            unresolved.add("审查反馈：" + state.getLastReviewReason());
        }
        if (state.hasPendingEvidenceRecovery()) {
            unresolved.add("补证据约束：下一步必须更换数据源或检索参数，并产生新增有效证据");
        }
        ResearchPlan revised = new ResearchPlan(
                original.goal(), original.hypotheses(), original.requiredEvidence(),
                new ArrayList<>(state.getCompletedTools()), unresolved,
                original.plannerMode(), original.version()
        );
        return new PlannerOutput<>(
                revised,
                output.degraded(),
                output.degradedReason(),
                output.inputTokens(),
                output.outputTokens(),
                output.durationMs()
        );
    }

    private <T> LlmGenerationResult generateStructured(String prompt, Class<T> targetType) {
        RuntimeException lastFailure = null;
        String currentPrompt = prompt;
        for (int attempt = 1; attempt <= MAX_STRUCTURE_ATTEMPTS; attempt++) {
            try {
                LlmGenerationResult result = llmClient.generateWithMetadata(currentPrompt, LlmClient.ModelType.SMART);
                objectMapper.readValue(cleanJson(result.text()), targetType);
                return result;
            } catch (RuntimeException | JsonProcessingException exception) {
                lastFailure = exception instanceof RuntimeException runtimeException
                        ? runtimeException : new IllegalStateException(exception);
                currentPrompt = prompt + "\n上次输出不是合法结构，请只返回符合格式的 JSON。";
            }
        }
        throw lastFailure == null ? new IllegalStateException("Planner 未返回合法结构") : lastFailure;
    }

    private ResearchPlan fallbackPlan(ResearchRunRequest request) {
        String question = request.getResearchQuestion();
        List<String> hypotheses = new ArrayList<>();
        List<String> evidence = new ArrayList<>(List.of("证券主体信息", "可追溯原始证据", "确定性金融指标"));
        if (containsAny(question, "风险", "波动", "下跌", "偿债")) {
            hypotheses.add("当前财务或市场证据可能暴露需要优先复核的风险因素");
            evidence.add("风险维度与行情观察");
        }
        if (containsAny(question, "毛利率", "营收", "利润", "现金流", "roe", "财务")) {
            hypotheses.add("财务指标变化可以由已披露报表数据验证");
            evidence.add("财务报表及同比口径");
        }
        if (containsAny(question, "价格", "行情", "估值", "pe", "pb", "技术")) {
            hypotheses.add("行情和估值证据可能影响问题结论");
            evidence.add("行情和估值快照");
        }
        if (hypotheses.isEmpty()) {
            hypotheses.add("需要结合财务、行情和公开材料验证研究问题");
        }
        return new ResearchPlan(
                question,
                hypotheses,
                evidence.stream().distinct().toList(),
                List.of(),
                List.of("当前尚未执行任何研究工具"),
                "DETERMINISTIC_FALLBACK",
                PLANNER_VERSION
        );
    }

    private AgentAction fallbackAction(AgentState state, ResearchToolRegistry registry) {
        if (state.getSubject() == null) {
            return AgentAction.call("resolve_security", "先解析并约束研究主体");
        }
        if (state.hasPendingEvidenceRecovery()) {
            return fallbackEvidenceRecoveryAction(state, registry);
        }
        String question = state.getRequest().getResearchQuestion().toLowerCase(Locale.ROOT);
        List<String> desiredProviders = desiredProviders(state.getRequest(), question);
        for (String tool : desiredProviders) {
            if (!state.getCompletedTools().contains(tool)) {
                return AgentAction.call(tool, "根据研究问题补充对应证据");
            }
        }
        long effectiveEvidence = state.getSnapshot() == null ? 0L : state.getSnapshot().evidenceItems().stream()
                .filter(FinancialEvidenceItem::effective)
                .count();
        if (effectiveEvidence < 3) {
            for (String tool : List.of(
                    "get_financial_statements",
                    "get_market_snapshot",
                    "retrieve_uploaded_reports",
                    "search_public_evidence"
            )) {
                if (!state.getCompletedTools().contains(tool)) {
                    return AgentAction.call(tool, "当前有效证据不足，尝试补充其他允许来源");
                }
            }
            return new AgentAction(
                    AgentActionType.STOP_INSUFFICIENT_EVIDENCE,
                    List.of(),
                    "所有允许的数据工具均已执行，但有效证据仍少于 3 条"
            );
        }
        if (state.getMetrics().isEmpty()) {
            return AgentAction.call("calculate_financial_metrics", "用确定性代码计算关键金融数字");
        }
        if (state.getRiskAssessment() == null) {
            return AgentAction.call("assess_financial_risk", "补齐最终报告需要的结构化风险判断");
        }
        if (needsBullBear(question) && state.getBullBearResearch() == null) {
            return AgentAction.call("build_bull_bear_cases", "研究问题需要正反条件化论据");
        }
        if (!state.getCompletedTools().contains("check_evidence_coverage")) {
            return AgentAction.call("check_evidence_coverage", "综合前执行证据覆盖检查");
        }
        return AgentAction.synthesize("主体、证据、指标和风险信息已满足综合条件");
    }

    /** 在确定性降级模式下优先切换证据来源，来源耗尽后使用新的检索 query。 */
    private AgentAction fallbackEvidenceRecoveryAction(AgentState state, ResearchToolRegistry registry) {
        Set<String> availableEvidenceTools = evidenceToolNames(registry);
        LinkedHashSet<String> candidates = new LinkedHashSet<>(desiredProviders(
                state.getRequest(), state.getRequest().getResearchQuestion().toLowerCase(Locale.ROOT)
        ));
        candidates.addAll(List.of(
                "get_financial_statements",
                "get_market_snapshot",
                "retrieve_uploaded_reports",
                "search_public_evidence"
        ));
        for (String toolName : candidates) {
            if (!availableEvidenceTools.contains(toolName)
                    || state.getCompletedTools().contains(toolName)
                    || !allowedBySearchMode(toolName, state.getRequest().getSearchMode())) {
                continue;
            }
            return AgentAction.call(toolName, "质量门禁要求切换到尚未使用的证据来源");
        }
        if (availableEvidenceTools.contains("search_public_evidence")
                && !"document".equals(state.getRequest().getSearchMode())) {
            int recoveryNo = state.getEvidenceRecoveryDirective().recoveryNo();
            int attemptNo = state.getEvidenceRecoveryDirective().attempts().size() + 1;
            String issueFocus = String.join(" ", state.getEvidenceRecoveryDirective().issueCodes());
            String query = "%s %s 交叉验证 补充证据 第%d轮第%d次".formatted(
                    state.getRequest().getResearchQuestion(), issueFocus, recoveryNo, attemptNo
            ).replaceAll("\\s+", " ").trim();
            return new AgentAction(
                    AgentActionType.CALL_TOOL,
                    List.of(new ToolInvocation("search_public_evidence", Map.of("query", query))),
                    "其他允许来源已使用，改用新的问题导向检索参数补证据"
            );
        }
        return new AgentAction(
                AgentActionType.STOP_INSUFFICIENT_EVIDENCE,
                List.of(),
                "允许的证据来源和差异化检索方式均已耗尽"
        );
    }

    private Set<String> evidenceToolNames(ResearchToolRegistry registry) {
        Collection<com.zzy.finsight.agent.tool.ResearchTool<?>> tools = registry == null ? null : registry.all();
        if (tools == null) {
            return Set.of();
        }
        return tools.stream()
                .filter(com.zzy.finsight.agent.tool.ResearchTool::producesEvidence)
                .map(com.zzy.finsight.agent.tool.ResearchTool::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean allowedBySearchMode(String toolName, String searchMode) {
        if ("get_company_profile".equals(toolName)) {
            return true;
        }
        if ("document".equals(searchMode)) {
            return "retrieve_uploaded_reports".equals(toolName);
        }
        if ("web".equals(searchMode)) {
            return !"retrieve_uploaded_reports".equals(toolName);
        }
        return true;
    }

    private List<String> desiredProviders(ResearchRunRequest request, String question) {
        List<String> tools = new ArrayList<>();
        tools.add("get_company_profile");
        boolean financial = containsAny(question, "财务", "营收", "利润", "毛利", "现金流", "roe", "负债", "估值", "etf", "基金");
        boolean market = containsAny(question, "行情", "价格", "波动", "新闻", "事件", "催化", "风险", "技术", "pe", "pb");
        boolean publicEvidence = containsAny(question, "新闻", "事件", "公告", "原因", "催化", "舆情", "监管");
        if ("document".equals(request.getSearchMode())) {
            tools.add("retrieve_uploaded_reports");
        } else if ("web".equals(request.getSearchMode())) {
            tools.add(financial ? "get_financial_statements" : "get_market_snapshot");
        } else {
            if (financial || !market) {
                tools.add("get_financial_statements");
            }
            if (market || !financial) {
                tools.add("get_market_snapshot");
            }
            tools.add("retrieve_uploaded_reports");
        }
        if (publicEvidence && !"document".equals(request.getSearchMode())) {
            tools.add("search_public_evidence");
        }
        return tools.stream().distinct().toList();
    }

    private Map<String, Object> stateSummary(AgentState state) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("question", state.getRequest().getResearchQuestion());
        summary.put("plan", state.getPlan());
        summary.put("turnNo", state.getTurnNo());
        summary.put("toolCallCount", state.getToolCallCount());
        summary.put("completedTools", state.getCompletedTools());
        summary.put("toolInvocationHistory", state.getToolInvocationHistory());
        summary.put("observations", state.getObservations());
        summary.put("subjectResolved", state.getSubject() != null);
        summary.put("effectiveEvidenceCount", state.getSnapshot() == null ? 0L
                : state.getSnapshot().evidenceItems().stream().filter(FinancialEvidenceItem::effective).count());
        summary.put("metricsReady", !state.getMetrics().isEmpty());
        summary.put("riskReady", state.getRiskAssessment() != null);
        summary.put("lastReviewReason", state.getLastReviewReason());
        summary.put("evidenceRecovery", state.getEvidenceRecoveryDirective());
        return summary;
    }

    private void validatePlan(ResearchPlan plan) {
        if (plan == null || plan.goal().isBlank() || plan.requiredEvidence().isEmpty()) {
            throw new IllegalArgumentException("Planner 计划缺少目标或证据需求");
        }
    }

    private void validateActionShape(AgentAction action) {
        if (action == null) {
            throw new IllegalArgumentException("Planner 动作为空");
        }
        boolean toolAction = action.type() == AgentActionType.CALL_TOOL
                || action.type() == AgentActionType.CALL_TOOLS_PARALLEL;
        if (toolAction && action.toolCalls().isEmpty()) {
            throw new IllegalArgumentException("工具动作缺少 toolCalls");
        }
    }

    /** 校验待补证据状态下的 LLM 动作必须是新的证据调用或明确停止。 */
    private void validateEvidenceRecoveryAction(
            AgentAction action,
            AgentState state,
            ResearchToolRegistry registry
    ) {
        if (!state.hasPendingEvidenceRecovery()
                || action.type() == AgentActionType.STOP_INSUFFICIENT_EVIDENCE) {
            return;
        }
        if (action.type() != AgentActionType.CALL_TOOL
                && action.type() != AgentActionType.CALL_TOOLS_PARALLEL) {
            throw new IllegalArgumentException("EVIDENCE_RECOVERY_ACTION_REQUIRED");
        }
        for (ToolInvocation invocation : action.toolCalls()) {
            if (!registry.require(invocation.toolName()).producesEvidence()) {
                throw new IllegalArgumentException("EVIDENCE_RECOVERY_TOOL_REQUIRED");
            }
            if (state.getToolInvocationHistory().contains(invocation)) {
                throw new IllegalArgumentException("EVIDENCE_RECOVERY_NOT_NOVEL");
            }
            if (state.getCompletedTools().contains(invocation.toolName())
                    && invocation.arguments().isEmpty()) {
                throw new IllegalArgumentException("EVIDENCE_RECOVERY_ARGUMENTS_REQUIRED");
            }
        }
    }

    private boolean needsBullBear(String question) {
        return containsAny(question, "多空", "风险", "机会", "利好", "利空", "投资", "前景");
    }

    private boolean containsAny(String text, String... words) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String cleanJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).trim();
            }
        }
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        return objectStart >= 0 && objectEnd >= objectStart
                ? text.substring(objectStart, objectEnd + 1) : text;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法构建 Planner 上下文", exception);
        }
    }

    private String classify(Exception exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("not configured")) {
            return "LLM_NOT_CONFIGURED";
        }
        if (exception instanceof JsonProcessingException || message.contains("JSON")) {
            return "LLM_INVALID_STRUCTURE";
        }
        if (message.startsWith("EVIDENCE_RECOVERY_")) {
            return "LLM_INVALID_ACTION";
        }
        return "LLM_CALL_FAILED";
    }
}
