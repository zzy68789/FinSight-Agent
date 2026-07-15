package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.analysis.FinancialMetricEngine;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.infrastructure.provider.PublicMarketDataProvider;
import com.zzy.finsight.infrastructure.provider.TushareMarketDataProvider;
import com.zzy.finsight.llm.LangChain4jLlmClient;
import com.zzy.finsight.rag.HybridRagRetriever;
import com.zzy.finsight.rag.InMemoryVectorDocumentStore;
import com.zzy.finsight.rag.OpenAiCompatibleEmbeddingClient;
import com.zzy.finsight.rag.RagDocumentChunk;
import com.zzy.finsight.search.EnhancedSearchService;
import com.zzy.finsight.search.TavilyExtractClient;
import com.zzy.finsight.search.TavilySearchSource;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

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

class LiveProviderEvaluationTest {

    @Test
    void runsNonBlockingStockAndEtfSmokeOnlyWhenExplicitlyEnabled() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EvaluationReportWriter reportWriter = new EvaluationReportWriter(objectMapper);
        boolean enabled = Boolean.getBoolean("finsight.eval.live.enabled");
        String apiKey = System.getenv("API_KEY");
        String tavilyKey = System.getenv("TAVILY_API_KEY");
        String tushareKey = System.getenv("TUSHARE_API_KEY");
        if (!enabled || blank(apiKey) || blank(tavilyKey) || blank(tushareKey)) {
            writeSkipped(reportWriter, enabled ? "缺少 API_KEY、TAVILY_API_KEY 或 TUSHARE_API_KEY" : "未开启真实数据冒烟");
            Assumptions.assumeTrue(false, "真实数据冒烟未启用或密钥不完整，状态为 SKIPPED");
        }

        RestClient.Builder restClientBuilder = RestClient.builder();
        TushareMarketDataProvider tushare = new TushareMarketDataProvider(
                restClientBuilder, objectMapper, true, "https://api.tushare.pro", tushareKey, "10s"
        );
        TavilySearchSource tavily = new TavilySearchSource(RestClient.builder(), tavilyKey);
        PublicMarketDataProvider publicProvider = new PublicMarketDataProvider(
                new EnhancedSearchService(List.of(tavily), 1),
                new TavilyExtractClient(RestClient.builder(), tavilyKey, 10, 2),
                3
        );
        String baseUrl = System.getProperty("finsight.eval.base-url", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String modelName = System.getProperty("finsight.eval.live-model", "qwen3.7-max");
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName).temperature(0.0d)
                .timeout(Duration.ofSeconds(120)).maxTokens(4096).maxRetries(0).build();
        InvestmentReportWriter writer = new InvestmentReportWriter(new LangChain4jLlmClient(model, model, model));
        OpenAiCompatibleEmbeddingClient embeddingClient = new OpenAiCompatibleEmbeddingClient(
                RestClient.builder(), baseUrl, apiKey, "text-embedding-v4"
        );
        List<StockSubject> subjects = List.of(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料", StockAssetType.EQUITY),
                new StockSubject("510300", "SH", "510300.SH", "沪深300ETF", "宽基指数", StockAssetType.ETF)
        );
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        List<EvaluationCaseResult> caseResults = new ArrayList<>();
        Map<String, BigDecimal> aggregates = new LinkedHashMap<>();
        int totalEffectiveEvidence = 0;
        int totalRagResults = 0;
        for (StockSubject subject : subjects) {
            List<FinancialEvidenceItem> evidence = new ArrayList<>();
            evidence.addAll(tushare.collect(subject, "latest", "hybrid"));
            evidence.addAll(publicProvider.collect(subject, "latest", "hybrid"));
            totalEffectiveEvidence += (int) evidence.stream().filter(FinancialEvidenceItem::effective).count();
            FinancialSnapshot snapshot = new FinancialSnapshot(subject, "latest", "hybrid", evidence, LocalDateTime.now());
            List<FinancialMetricResult> metrics = new FinancialMetricEngine().compute(snapshot);
            FinancialRiskAssessment risk = new FinancialRiskScorer().assess(metrics, evidence);
            String report = writer.write(snapshot, metrics, risk, null);
            CitationReviewResult review = new CitationReviewer().review(report, snapshot, metrics);
            FinancialComplianceReviewResult compliance = new FinancialComplianceReviewer().review(report, review);
            FinancialEvaluationResult evaluation = new FinancialEvaluator(objectMapper).evaluateOnline(report, snapshot, metrics);
            String actual = "PASS".equals(review.status()) && "PASS".equals(compliance.status())
                    && "PASS".equals(evaluation.status()) ? "PASS" : "WARN";
            caseResults.add(new EvaluationCaseResult(
                    "live-" + subject.ticker(), "live-provider", "PASS", actual, actual,
                    evaluation, evaluation.failedReasons()
            ));

            List<RagDocumentChunk> chunks = new ArrayList<>();
            for (int index = 0; index < evidence.size(); index++) {
                FinancialEvidenceItem item = evidence.get(index);
                if (item.excerpt() != null && !item.excerpt().isBlank()) {
                    chunks.add(new RagDocumentChunk(item.sourceName(), index, item.excerpt()));
                }
            }
            HybridRagRetriever retriever = new HybridRagRetriever(
                    new InMemoryVectorDocumentStore(embeddingClient), 0.20d
            );
            retriever.index(chunks);
            totalRagResults += retriever.retrieve(subject.companyName() + " 财务 风险", 5).size();
        }
        aggregates.put("live_effective_evidence_count", BigDecimal.valueOf(totalEffectiveEvidence));
        aggregates.put("live_rag_result_count", BigDecimal.valueOf(totalRagResults));
        aggregates.put("live_case_pass_rate", BigDecimal.valueOf(caseResults.stream()
                .filter(item -> "PASS".equals(item.actualStatus())).count())
                .divide(BigDecimal.valueOf(subjects.size()), 2, RoundingMode.HALF_UP));
        String status = caseResults.stream().allMatch(item -> "PASS".equals(item.actualStatus())) ? "PASS" : "WARN";
        EvaluationRunResult result = new EvaluationRunResult(
                runId("live"), EvaluationMode.LIVE_SMOKE, "live", commitId(), FinancialEvaluator.POLICY_VERSION,
                LlmJudgeEvaluator.PROMPT_VERSION, startedAt, LocalDateTime.now(),
                Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L), status,
                caseResults, List.of(), List.of(), aggregates,
                new EvaluationBaselineComparison("PASS", Map.of(), List.of(), List.of()),
                modelName, 0, 0, 0
        );
        Path output = reportWriter.write(Path.of("target", "evaluation"), result);

        assertThat(output.resolve("results.json")).exists();
        assertThat(output.resolve("summary.md")).exists();
    }

    private void writeSkipped(EvaluationReportWriter writer, String reason) {
        EvaluationRunResult result = new EvaluationRunResult(
                runId("live-skipped"), EvaluationMode.LIVE_SMOKE, "live", commitId(), FinancialEvaluator.POLICY_VERSION,
                LlmJudgeEvaluator.PROMPT_VERSION, LocalDateTime.now(), LocalDateTime.now(), 0L, "SKIPPED",
                List.of(), List.of(), List.of(), Map.of("skipped", BigDecimal.ONE),
                new EvaluationBaselineComparison("PASS", Map.of(), List.of(), List.of(reason)),
                "", 0, 0, 0
        );
        writer.write(Path.of("target", "evaluation"), result);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String runId(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
    }

    private String commitId() {
        String sha = System.getenv("GITHUB_SHA");
        return sha == null || sha.isBlank() ? "LOCAL" : sha;
    }
}
