package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.review.FinancialEvaluator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialEvaluationRegressionTest {

    @Test
    void runsVersionedFrozenDatasetAndWritesJsonAndMarkdownReports() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load("evaluation/dataset-v1.json");
        EvaluationBaseline baseline = loadBaseline(objectMapper);
        EvaluationReportWriter reportWriter = new EvaluationReportWriter(objectMapper);
        FinancialEvaluationRunner runner = new FinancialEvaluationRunner(
                new FinancialEvaluator(objectMapper),
                new RetrievalEvaluator(),
                new EvaluationBaselineComparator(),
                reportWriter
        );

        EvaluationRunResult result = runner.runDeterministic(
                dataset,
                baseline,
                commitId(),
                Path.of("target", "evaluation")
        );

        assertThat(dataset.cases()).hasSize(20);
        assertThat(dataset.cases()).filteredOn(item -> item.scenario().endsWith("positive")).hasSize(12);
        assertThat(dataset.cases()).filteredOn(item -> !item.scenario().endsWith("positive")).hasSize(8);
        assertThat(dataset.retrievalCases()).hasSizeGreaterThanOrEqualTo(24);
        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.aggregateMetrics().get("recall_at_3")).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.90"));
        assertThat(result.aggregateMetrics().get("mrr")).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.80"));
        assertThat(result.aggregateMetrics().get("ndcg_at_3")).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.85"));
        Path runDirectory = Path.of("target", "evaluation", result.runId());
        assertThat(runDirectory.resolve("results.json")).exists();
        assertThat(runDirectory.resolve("summary.md")).exists();

        if (Boolean.getBoolean("finsight.eval.update-baseline")) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    Path.of("src", "test", "resources", "evaluation", "baseline-v1.json").toFile(),
                    new EvaluationBaseline("baseline-v1", result.aggregateMetrics())
            );
        }
    }

    private EvaluationBaseline loadBaseline(ObjectMapper objectMapper) throws Exception {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("evaluation/baseline-v1.json")) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, EvaluationBaseline.class);
        }
    }

    private String commitId() {
        String githubSha = System.getenv("GITHUB_SHA");
        return githubSha == null || githubSha.isBlank() ? "LOCAL" : githubSha;
    }
}
