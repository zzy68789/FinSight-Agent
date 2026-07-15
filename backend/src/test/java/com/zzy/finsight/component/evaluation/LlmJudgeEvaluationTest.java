package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.llm.LangChain4jLlmClient;
import com.zzy.finsight.llm.LlmClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmJudgeEvaluationTest {

    @Test
    void runsRealGenerationAndJudgeOnlyWhenExplicitlyEnabled() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EvaluationReportWriter reportWriter = new EvaluationReportWriter(objectMapper);
        boolean enabled = Boolean.getBoolean("finsight.eval.judge.enabled");
        String apiKey = System.getenv("API_KEY");
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            writeSkipped(reportWriter, enabled ? "缺少 API_KEY" : "未开启 Judge 评测");
            Assumptions.assumeTrue(false, enabled ? "缺少 API_KEY，Judge 评测已跳过" : "Judge 评测默认关闭");
        }

        String baseUrl = System.getProperty(
                "finsight.eval.base-url",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
        );
        String modelName = System.getProperty("finsight.eval.judge-model", "qwen3.7-max");
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.0d)
                .timeout(Duration.ofSeconds(Long.getLong("finsight.eval.judge.timeout-seconds", 120L)))
                .maxTokens(4096)
                .maxRetries(0)
                .build();
        LlmClient llmClient = new LangChain4jLlmClient(model, model, model);
        InvestmentReportWriter investmentWriter = new InvestmentReportWriter(llmClient);
        LlmJudgeEvaluator judgeEvaluator = new LlmJudgeEvaluator(llmClient, objectMapper);
        EvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load("evaluation/dataset-v1.json");
        int caseLimit = Integer.getInteger("finsight.eval.judge.case-limit", 12);
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        List<LlmJudgeEvaluationResult> judgeResults = new ArrayList<>();
        for (EvaluationCaseFixture fixture : dataset.cases().stream()
                .filter(item -> item.scenario().endsWith("positive"))
                .limit(Math.max(1, caseLimit))
                .toList()) {
            FinancialSnapshot snapshot = snapshot(fixture);
            List<FinancialMetricResult> metrics = metrics(fixture);
            FinancialRiskAssessment risk = new FinancialRiskScorer().assess(metrics, snapshot.evidenceItems());
            String report = investmentWriter.write(snapshot, metrics, risk, null);
            judgeResults.add(judgeEvaluator.evaluate(
                    fixture.caseId(), report, snapshot.evidenceItems(), fixture.requiredKeypoints()
            ));
        }
        Map<String, BigDecimal> aggregates = judgeAggregates(judgeResults);
        long durationMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        String status = judgeResults.stream().anyMatch(result -> !"PASS".equals(result.status())) ? "WARN" : "PASS";
        EvaluationRunResult result = new EvaluationRunResult(
                runId("judge"),
                EvaluationMode.JUDGE,
                dataset.version(),
                commitId(),
                com.zzy.finsight.component.review.FinancialEvaluator.POLICY_VERSION,
                LlmJudgeEvaluator.PROMPT_VERSION,
                startedAt,
                LocalDateTime.now(),
                durationMs,
                status,
                List.of(),
                List.of(),
                judgeResults,
                aggregates,
                new EvaluationBaselineComparison("PASS", Map.of(), List.of(), List.of()),
                judgeResults.stream().map(LlmJudgeEvaluationResult::modelName).filter(value -> !value.isBlank())
                        .findFirst().orElse(modelName),
                judgeResults.stream().mapToInt(LlmJudgeEvaluationResult::inputTokens).sum(),
                judgeResults.stream().mapToInt(LlmJudgeEvaluationResult::outputTokens).sum(),
                judgeResults.stream().mapToInt(LlmJudgeEvaluationResult::totalTokens).sum()
        );
        Path output = reportWriter.write(Path.of("target", "evaluation"), result);

        assertThat(judgeResults).isNotEmpty();
        assertThat(output.resolve("results.json")).exists();
        assertThat(output.resolve("summary.md")).exists();
    }

    private FinancialSnapshot snapshot(EvaluationCaseFixture fixture) {
        StockSubject subject = new StockSubject(
                fixture.ticker(), fixture.exchange(), fixture.ticker() + "." + fixture.exchange(),
                fixture.companyName(), fixture.industry(), StockAssetType.valueOf(fixture.assetType())
        );
        FinancialEvidenceItem evidence = new FinancialEvidenceItem(
                "FROZEN_FIXTURE", fixture.caseId(), "fixture://" + fixture.caseId(), null,
                fixture.reportPeriod(), fixture.metricName(), null, null, fixture.evidenceExcerpt(),
                new BigDecimal("0.90"), LocalDateTime.of(2026, 7, 1, 9, 30), fixture.evidenceIssueCode()
        );
        return new FinancialSnapshot(subject, fixture.reportPeriod(), "frozen", List.of(evidence),
                LocalDateTime.of(2026, 7, 1, 9, 30));
    }

    private List<FinancialMetricResult> metrics(EvaluationCaseFixture fixture) {
        return List.of(new FinancialMetricResult(
                fixture.metricName(), null, fixture.metricDisplayValue(), "冻结评测指标", "evaluation-v1",
                "OK", "", List.of(fixture.metricName())
        ));
    }

    private Map<String, BigDecimal> judgeAggregates(List<LlmJudgeEvaluationResult> results) {
        Map<String, List<BigDecimal>> values = new LinkedHashMap<>();
        results.stream().flatMap(result -> result.metricScores().stream()).forEach(score -> values
                .computeIfAbsent(score.metricName(), ignored -> new ArrayList<>()).add(score.score()));
        Map<String, BigDecimal> aggregates = new LinkedHashMap<>();
        values.forEach((name, scores) -> aggregates.put(name, scores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 4, RoundingMode.HALF_UP)));
        aggregates.put("average_total_tokens", results.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(
                results.stream().mapToInt(LlmJudgeEvaluationResult::totalTokens).average().orElse(0.0d)
        ).setScale(2, RoundingMode.HALF_UP));
        List<Long> durations = results.stream().map(LlmJudgeEvaluationResult::durationMs).sorted().toList();
        int p95Index = durations.isEmpty() ? 0 : Math.max(0, (int) Math.ceil(durations.size() * 0.95d) - 1);
        aggregates.put("p95_duration_ms", durations.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(durations.get(p95Index)));
        return aggregates;
    }

    private void writeSkipped(EvaluationReportWriter writer, String reason) {
        LlmJudgeEvaluationResult skipped = new LlmJudgeEvaluationResult(
                "judge-suite", "SKIPPED", List.of(), List.of(), List.of(), LlmJudgeEvaluator.PROMPT_VERSION,
                "", 0, 0, 0, "UNKNOWN", 0L, reason
        );
        EvaluationRunResult result = new EvaluationRunResult(
                runId("judge-skipped"), EvaluationMode.JUDGE, "dataset-v1", commitId(),
                com.zzy.finsight.component.review.FinancialEvaluator.POLICY_VERSION,
                LlmJudgeEvaluator.PROMPT_VERSION, LocalDateTime.now(), LocalDateTime.now(), 0L, "SKIPPED",
                List.of(), List.of(), List.of(skipped), Map.of(),
                new EvaluationBaselineComparison("PASS", Map.of(), List.of(), List.of()),
                "", 0, 0, 0
        );
        writer.write(Path.of("target", "evaluation"), result);
    }

    private String runId(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
    }

    private String commitId() {
        String sha = System.getenv("GITHUB_SHA");
        return sha == null || sha.isBlank() ? "LOCAL" : sha;
    }
}
