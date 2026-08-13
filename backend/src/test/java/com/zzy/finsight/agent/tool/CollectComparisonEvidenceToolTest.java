package com.zzy.finsight.agent.tool;

import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.agent.ResearchIntent;
import com.zzy.finsight.infrastructure.provider.FinancialDataProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectComparisonEvidenceToolTest {
    private final StockCodeResolver resolver = new StockCodeResolver();

    @Test
    void tagsEveryEvidenceWithSecurityAndIndependentSnapshotId() {
        FinancialDataProvider provider = provider(List.of(evidence("PE_TTM", "18.5")));
        CollectComparisonEvidenceTool tool = new CollectComparisonEvidenceTool(resolver, List.of(provider));

        ToolResult result = tool.execute(context(List.of("000858.SZ")), new NoToolArguments());

        ToolPayload.ComparisonEvidence payload = (ToolPayload.ComparisonEvidence) result.payload();
        assertThat(payload.snapshots()).hasSize(1);
        assertThat(payload.snapshots().get(0).status()).isEqualTo("SUCCESS");
        assertThat(payload.evidence()).allSatisfy(item -> {
            assertThat(item.subjectCode()).isEqualTo("000858.SZ");
            assertThat(item.comparisonSnapshotId()).isEqualTo(payload.snapshots().get(0).snapshotId());
        });
    }

    @Test
    void convertsProviderFailureToExplicitDataMissingEvidence() {
        FinancialDataProvider provider = new FinancialDataProvider() {
            public String name() { return "失败数据源"; }
            public List<FinancialEvidenceItem> collect(long ownerId, StockSubject subject, String period, String mode) {
                throw new IllegalStateException("不可用");
            }
        };
        CollectComparisonEvidenceTool tool = new CollectComparisonEvidenceTool(resolver, List.of(provider));

        ToolPayload.ComparisonEvidence payload = (ToolPayload.ComparisonEvidence) tool.execute(
                context(List.of("000858.SZ")), new NoToolArguments()
        ).payload();

        assertThat(payload.snapshots().get(0).status()).isEqualTo("DATA_MISSING");
        assertThat(payload.evidence()).singleElement()
                .satisfies(item -> assertThat(item.issueCode()).isEqualTo("DATA_MISSING"));
    }

    private ToolContext context(List<String> comparisons) {
        StockSubject primary = resolver.resolve("600519");
        FinancialSnapshot snapshot = new FinancialSnapshot(
                primary, "20260813", "hybrid", List.of(), LocalDateTime.now()
        );
        return new ToolContext(
                7L,
                11L,
                new ToolResearchRequest(
                        primary.fullCode(), "比较估值", ResearchIntent.VALUATION_RISK,
                        LocalDate.of(2026, 8, 13), "2Y", "standard", "hybrid", comparisons
                ),
                primary,
                snapshot,
                List.of(),
                null,
                null
        );
    }

    private FinancialDataProvider provider(List<FinancialEvidenceItem> evidence) {
        return new FinancialDataProvider() {
            public String name() { return "测试数据源"; }
            public List<FinancialEvidenceItem> collect(long ownerId, StockSubject subject, String period, String mode) {
                return evidence;
            }
        };
    }

    private FinancialEvidenceItem evidence(String metricName, String value) {
        BigDecimal decimal = new BigDecimal(value);
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET", "测试数据源", "", null, "20260813", metricName,
                decimal, decimal, metricName + "：" + value, BigDecimal.ONE, LocalDateTime.now(), ""
        );
    }
}
