package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.llm.LlmClient;
import com.zzy.finsight.llm.LlmGenerationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用独立可配置模型对冻结证据范围内的报告质量执行辅助评分。
 */
@Component
public class LlmJudgeEvaluator {
    public static final String PROMPT_VERSION = "financial-judge-v1-evidence-only";
    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmJudgeEvaluator(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /** 依据冻结证据评价相关性、忠实度、完整性和研究辅助价值。 */
    public LlmJudgeEvaluationResult evaluate(
            String caseId,
            String report,
            List<FinancialEvidenceItem> evidenceItems,
            List<String> requiredKeypoints
    ) {
        try {
            LlmGenerationResult generation = llmClient.generateWithMetadata(
                    buildPrompt(report, evidenceItems, requiredKeypoints),
                    LlmClient.ModelType.JUDGE
            );
            return parse(caseId, generation);
        } catch (RuntimeException exception) {
            return error(caseId, exception.getMessage(), null);
        }
    }

    /** 解析 Judge 结构化响应；非法结果显式返回 ERROR。 */
    public LlmJudgeEvaluationResult parse(String caseId, LlmGenerationResult generation) {
        if (generation == null || generation.text().isBlank()) {
            return error(caseId, "Judge 返回空响应", generation);
        }
        try {
            JudgePayload payload = objectMapper.readValue(stripFence(generation.text()), JudgePayload.class);
            List<FinancialEvaluationMetricScore> scores = List.of(
                    metric("judge_relevance", payload.relevance()),
                    metric("judge_faithfulness", payload.faithfulness()),
                    metric("judge_completeness", payload.completeness()),
                    metric("judge_research_helpfulness", payload.researchHelpfulness())
            );
            boolean warning = scores.stream().anyMatch(score -> "WARN".equals(score.status()));
            return new LlmJudgeEvaluationResult(
                    caseId,
                    warning ? "WARN" : "PASS",
                    scores,
                    payload.issues(),
                    payload.rationale(),
                    PROMPT_VERSION,
                    generation.modelName(),
                    generation.inputTokens(),
                    generation.outputTokens(),
                    generation.totalTokens(),
                    generation.finishReason(),
                    generation.durationMs(),
                    ""
            );
        } catch (Exception exception) {
            return error(caseId, "Judge 结构化响应解析失败：" + exception.getMessage(), generation);
        }
    }

    private String buildPrompt(
            String report,
            List<FinancialEvidenceItem> evidenceItems,
            List<String> requiredKeypoints
    ) {
        try {
            String evidenceJson = objectMapper.writeValueAsString(evidenceItems == null ? List.of() : evidenceItems);
            String keypointsJson = objectMapper.writeValueAsString(requiredKeypoints == null ? List.of() : requiredKeypoints);
            return """
                    你是金融投研报告离线评测员。只能依据给定冻结证据评分，不得使用外部知识，也不得给出投资建议。
                    对以下四项分别给出 0 到 1 的小数：
                    - relevance：报告是否围绕指定证券和研究任务。
                    - faithfulness：事实与数字是否能由冻结证据支持。
                    - completeness：是否覆盖必备关键点。
                    - research_helpfulness：是否清晰呈现事实、风险和后续观察点。
                    只返回一个 JSON 对象，字段必须为 relevance、faithfulness、completeness、research_helpfulness、issues、rationale；后两项必须是字符串数组。

                    必备关键点：%s
                    冻结证据：%s
                    待评报告：
                    %s
                    """.formatted(keypointsJson, evidenceJson, report == null ? "" : report);
        } catch (Exception exception) {
            throw new IllegalStateException("构建 Judge 提示词失败", exception);
        }
    }

    private FinancialEvaluationMetricScore metric(String name, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须位于 0 到 1");
        }
        BigDecimal score = value.setScale(2, RoundingMode.HALF_UP);
        return new FinancialEvaluationMetricScore(
                name,
                score,
                WARNING_THRESHOLD,
                score.compareTo(WARNING_THRESHOLD) >= 0 ? "PASS" : "WARN",
                "Judge 单项低于 0.80 时产生非阻断告警",
                FinancialEvaluationMetricScore.Category.JUDGE,
                FinancialEvaluationMetricScore.GateLevel.ADVISORY,
                FinancialEvaluationMetricScore.Direction.HIGHER_BETTER
        );
    }

    private LlmJudgeEvaluationResult error(
            String caseId,
            String message,
            LlmGenerationResult generation
    ) {
        LlmGenerationResult metadata = generation == null
                ? new LlmGenerationResult("", "", 0, 0, 0, "UNKNOWN", 0L)
                : generation;
        return new LlmJudgeEvaluationResult(
                caseId,
                "ERROR",
                List.of(),
                List.of(),
                List.of(),
                PROMPT_VERSION,
                metadata.modelName(),
                metadata.inputTokens(),
                metadata.outputTokens(),
                metadata.totalTokens(),
                metadata.finishReason(),
                metadata.durationMs(),
                message
        );
    }

    private String stripFence(String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7).strip();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring(3).strip();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).strip();
        }
        return normalized;
    }

    private record JudgePayload(
            BigDecimal relevance,
            BigDecimal faithfulness,
            BigDecimal completeness,
            @com.fasterxml.jackson.annotation.JsonProperty("research_helpfulness") BigDecimal researchHelpfulness,
            List<String> issues,
            List<String> rationale
    ) {
        private JudgePayload {
            issues = issues == null ? List.of() : List.copyOf(issues);
            rationale = rationale == null ? List.of() : List.copyOf(rationale);
        }
    }
}
