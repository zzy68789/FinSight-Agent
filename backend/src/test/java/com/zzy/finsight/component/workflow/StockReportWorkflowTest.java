package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.component.analysis.FinancialMetricEngine;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;
import com.zzy.finsight.component.marketdata.FinancialSnapshotBuilder;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StockReportWorkflowTest {

    private final StockReportWorkflow workflow = new StockReportWorkflow(
            new StockCodeResolver(),
            new FinancialSnapshotBuilder(List.of(), Runnable::run),
            new FinancialMetricEngine(),
            new FinancialRiskScorer(),
            new InvestmentReportWriter((prompt, modelType) -> {
                throw new IllegalStateException("测试环境未配置 LLM");
            }),
            new CitationReviewer(),
            new FinancialComplianceReviewer(),
            new FinancialEvaluator(new ObjectMapper())
    );

    @Test
    void evaluatesReportWhenTickerExistsInDefaultEvalSet() {
        FinancialSnapshot snapshot = new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "latest",
                "hybrid",
                List.of(evidence("NEWS_SUMMARY", "公司经营平稳，未见重大利空。")),
                LocalDateTime.of(2026, 7, 3, 10, 0)
        );
        String report = """
                ## 公司概况
                贵州茅台。
                ## 财务表现
                ROE：30.00%。
                ## 盈利能力与现金流
                现金流表现需持续复核。
                ## 主要风险
                数据缺口和市场波动。
                ## 引用与数据快照
                - [E1] PUBLIC_MARKET / 新闻 / latest / NEWS_SUMMARY / OK：公司经营平稳，未见重大利空。
                """;

        Optional<FinancialEvaluationResult> result = workflow.evaluation(
                report,
                snapshot,
                List.of(metric("ROE", "30.00%"))
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().ticker()).isEqualTo("600519");
    }

    private FinancialMetricResult metric(String name, String displayValue) {
        return new FinancialMetricResult(name, null, displayValue, name + "公式", "OK", "", List.of(name));
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                metricName,
                "",
                null,
                "latest",
                metricName,
                null,
                null,
                excerpt,
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 7, 3, 10, 0),
                ""
        );
    }
}
