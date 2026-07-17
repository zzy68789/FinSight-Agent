package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BullBearResearchAgentTest {
    private final BullBearResearchAgent agent = new BullBearResearchAgent();

    @Test
    void buildsEvidenceBoundBullAndBearCasesWithoutTradingAdvice() {
        FinancialSnapshot snapshot = new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260331",
                "hybrid",
                List.of(
                        evidence("OPERATING_REVENUE", "本期营业收入 539 亿元"),
                        evidence("OPERATING_REVENUE_PRIOR", "上年同期营业收入 506 亿元"),
                        evidence("TOTAL_LIABILITIES", "总负债 38 亿元"),
                        evidence("TOTAL_ASSETS", "总资产 50 亿元")
                ),
                LocalDateTime.of(2026, 7, 17, 10, 0)
        );
        List<FinancialMetricResult> metrics = List.of(
                metric("营收同比", "6.52%", "6.52", "OPERATING_REVENUE", "OPERATING_REVENUE_PRIOR"),
                metric("资产负债率", "76.00%", "76.00", "TOTAL_LIABILITIES", "TOTAL_ASSETS")
        );

        BullBearResearchResult result = agent.analyze(
                snapshot,
                metrics,
                new FinancialRiskAssessment(new BigDecimal("6.2"), "MEDIUM", List.of(), List.of())
        );

        assertThat(result.bullCases()).hasSize(1);
        assertThat(result.bullCases().get(0).evidenceRefs()).containsExactly("E1", "E2");
        assertThat(result.bearCases()).hasSize(1);
        assertThat(result.bearCases().get(0).evidenceRefs()).containsExactly("E3", "E4");
        assertThat(result.synthesis()).contains("不构成投资建议");
        assertThat(result.bullCases()).allMatch(claim -> !claim.statement().contains("买入"));
    }

    private FinancialMetricResult metric(
            String name,
            String display,
            String value,
            String... evidenceRefs
    ) {
        return new FinancialMetricResult(
                name,
                new BigDecimal(value),
                display,
                "测试公式",
                "v1",
                "OK",
                "",
                List.of(evidenceRefs)
        );
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "测试数据源",
                "https://example.com",
                null,
                "20260331",
                metricName,
                BigDecimal.ONE,
                BigDecimal.ONE,
                excerpt,
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 7, 17, 9, 0),
                ""
        );
    }
}
