package com.zzy.finsight.component.evaluation;

import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.domain.stock.FinancialEvaluationCase;
import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.rag.EmbeddingClient;
import com.zzy.finsight.rag.HybridRagRetriever;
import com.zzy.finsight.rag.InMemoryVectorDocumentStore;
import com.zzy.finsight.rag.RagDocumentChunk;
import com.zzy.finsight.rag.RagKnowledgeSpace;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行冻结报告规则、真实混合检索和历史基线比较的确定性离线回归。
 */
@Component
public class FinancialEvaluationRunner {
    private static final LocalDateTime FIXED_SNAPSHOT_TIME = LocalDateTime.of(2026, 7, 1, 9, 30);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private final FinancialEvaluator financialEvaluator;
    private final RetrievalEvaluator retrievalEvaluator;
    private final EvaluationBaselineComparator baselineComparator;
    private final EvaluationReportWriter reportWriter;

    public FinancialEvaluationRunner(
            FinancialEvaluator financialEvaluator,
            RetrievalEvaluator retrievalEvaluator,
            EvaluationBaselineComparator baselineComparator,
            EvaluationReportWriter reportWriter
    ) {
        this.financialEvaluator = financialEvaluator;
        this.retrievalEvaluator = retrievalEvaluator;
        this.baselineComparator = baselineComparator;
        this.reportWriter = reportWriter;
    }

    /** 执行不访问网络和外部模型的确定性回归，并写出双格式报告。 */
    public EvaluationRunResult runDeterministic(
            EvaluationDataset dataset,
            EvaluationBaseline baseline,
            String gitCommit,
            Path outputRoot
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        List<EvaluationCaseResult> caseResults = dataset.cases().stream()
                .map(fixture -> evaluateCase(fixture, dataset.defaultAbsoluteTolerance()))
                .toList();
        List<RetrievalEvaluationResult> retrievalResults = dataset.retrievalCases().stream()
                .map(this::evaluateRetrieval)
                .toList();
        Map<String, BigDecimal> aggregates = aggregate(caseResults, retrievalResults);
        EvaluationBaselineComparison comparison = baselineComparator.compare(aggregates, baseline.metrics());
        boolean failed = caseResults.stream().anyMatch(result -> "FAIL".equals(result.status()))
                || retrievalResults.stream().anyMatch(result -> "FAIL".equals(result.status()))
                || "FAIL".equals(comparison.status());
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        String runId = startedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        EvaluationRunResult result = new EvaluationRunResult(
                runId,
                EvaluationMode.DETERMINISTIC,
                dataset.version(),
                gitCommit == null || gitCommit.isBlank() ? "UNKNOWN" : gitCommit,
                FinancialEvaluator.POLICY_VERSION,
                LlmJudgeEvaluator.PROMPT_VERSION,
                startedAt,
                finishedAt,
                durationMs,
                failed ? "FAIL" : "PASS",
                caseResults,
                retrievalResults,
                List.of(),
                aggregates,
                comparison,
                "",
                0,
                0,
                0
        );
        reportWriter.write(outputRoot, result);
        return result;
    }

    private EvaluationCaseResult evaluateCase(EvaluationCaseFixture fixture, BigDecimal defaultTolerance) {
        FinancialSnapshot snapshot = snapshot(fixture);
        List<FinancialMetricResult> metrics = metrics(fixture);
        FinancialEvaluationResult evaluation = financialEvaluator.evaluate(
                new FinancialEvaluationCase(
                        fixture.ticker(),
                        fixture.companyName(),
                        fixture.reportPeriod(),
                        fixture.requiredKeypoints()
                ),
                fixture.report(),
                snapshot,
                metrics
        );
        List<String> failures = new ArrayList<>();
        if (!fixture.expectedStatus().equals(evaluation.status())) {
            failures.add("期望规则状态为 " + fixture.expectedStatus() + "，实际为 " + evaluation.status());
        }
        boolean metricMatches = metricMatches(fixture, defaultTolerance);
        if (metricMatches != fixture.expectedMetricMatch()) {
            failures.add("金融数值误差校验结果与样例期望不一致");
        }
        for (String forbiddenClaim : fixture.forbiddenClaims()) {
            if (fixture.report().contains(forbiddenClaim)) {
                failures.add("报告出现禁止结论：" + forbiddenClaim);
            }
        }
        return new EvaluationCaseResult(
                fixture.caseId(),
                fixture.scenario(),
                fixture.expectedStatus(),
                evaluation.status(),
                failures.isEmpty() ? "PASS" : "FAIL",
                evaluation,
                failures
        );
    }

    private boolean metricMatches(EvaluationCaseFixture fixture, BigDecimal tolerance) {
        BigDecimal expected = firstNumber(fixture.metricDisplayValue());
        String metricLine = fixture.report() == null ? "" : fixture.report().lines()
                .filter(line -> line.contains(fixture.metricName()))
                .findFirst()
                .orElse("");
        BigDecimal actual = firstNumber(metricLine);
        if (expected == null || actual == null) {
            return false;
        }
        return expected.subtract(actual).abs().compareTo(tolerance) <= 0;
    }

    private BigDecimal firstNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? new BigDecimal(matcher.group()) : null;
    }

    private RetrievalEvaluationResult evaluateRetrieval(RetrievalCaseFixture fixture) {
        EmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        HybridRagRetriever retriever = new HybridRagRetriever(
                new InMemoryVectorDocumentStore(embeddingClient),
                0.20d
        );
        List<RagDocumentChunk> chunks = fixture.corpus().stream()
                .map(item -> new RagDocumentChunk(item.source(), item.chunkIndex(), item.content()))
                .toList();
        Map<String, Integer> grades = new LinkedHashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            grades.put(chunks.get(index).chunkId(), fixture.corpus().get(index).relevanceGrade());
        }
        RagKnowledgeSpace space = RagKnowledgeSpace.named("evaluation-" + fixture.caseId());
        retriever.index(space, chunks);
        return retrievalEvaluator.evaluate(fixture.query(), retriever.retrieve(space, fixture.query(), 5), grades);
    }

    private FinancialSnapshot snapshot(EvaluationCaseFixture fixture) {
        StockAssetType assetType = StockAssetType.valueOf(fixture.assetType().toUpperCase(Locale.ROOT));
        StockSubject subject = new StockSubject(
                fixture.ticker(),
                fixture.exchange(),
                fixture.ticker() + "." + fixture.exchange(),
                fixture.companyName(),
                fixture.industry(),
                assetType
        );
        FinancialEvidenceItem evidence = new FinancialEvidenceItem(
                "FROZEN_FIXTURE",
                fixture.caseId(),
                "fixture://" + fixture.caseId(),
                null,
                fixture.reportPeriod(),
                fixture.metricName(),
                null,
                null,
                fixture.evidenceExcerpt(),
                new BigDecimal("0.90"),
                FIXED_SNAPSHOT_TIME,
                fixture.evidenceIssueCode()
        );
        return new FinancialSnapshot(subject, fixture.reportPeriod(), "frozen", List.of(evidence), FIXED_SNAPSHOT_TIME);
    }

    private List<FinancialMetricResult> metrics(EvaluationCaseFixture fixture) {
        if (fixture.metricName() == null || fixture.metricName().isBlank()) {
            return List.of();
        }
        return List.of(new FinancialMetricResult(
                fixture.metricName(),
                null,
                fixture.metricDisplayValue(),
                "冻结评测指标",
                "evaluation-v1",
                "OK",
                "",
                List.of(fixture.metricName())
        ));
    }

    private Map<String, BigDecimal> aggregate(
            List<EvaluationCaseResult> caseResults,
            List<RetrievalEvaluationResult> retrievalResults
    ) {
        Map<String, BigDecimal> aggregates = new LinkedHashMap<>();
        long passedCases = caseResults.stream().filter(result -> "PASS".equals(result.status())).count();
        aggregates.put("rule_case_pass_rate", ratio(passedCases, caseResults.size()));
        Map<String, List<BigDecimal>> retrievalValues = new LinkedHashMap<>();
        retrievalResults.stream()
                .flatMap(result -> result.metricScores().stream())
                .forEach(metric -> retrievalValues
                        .computeIfAbsent(metric.metricName(), ignored -> new ArrayList<>())
                        .add(metric.score()));
        retrievalValues.forEach((name, values) -> aggregates.put(name, average(values)));
        return aggregates;
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    /** 使用固定维度哈希向量确保测试中的混合检索可复现。 */
    private static final class DeterministicEmbeddingClient implements EmbeddingClient {
        private static final int DIMENSIONS = 128;

        @Override
        public List<Double> embed(String text) {
            double[] vector = new double[DIMENSIONS];
            if (text != null) {
                for (String token : text.toLowerCase(Locale.ROOT).split("[\\s,，。；;：:、]+")) {
                    if (!token.isBlank()) {
                        vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1.0d;
                    }
                }
            }
            List<Double> values = new ArrayList<>(DIMENSIONS);
            for (double value : vector) {
                values.add(value);
            }
            return values;
        }
    }
}
