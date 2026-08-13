package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.ComparisonSecuritySnapshot;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonReportAttributionTest {

    @Test
    void writesComparisonSnapshotMetricAndSubjectOwnedCitation() {
        StockSubject primary = new StockSubject(
                "510300", "SH", "510300.SH", "沪深300ETF", "ETF", StockAssetType.ETF
        );
        StockSubject comparison = new StockSubject(
                "510500", "SH", "510500.SH", "中证500ETF", "ETF", StockAssetType.ETF
        );
        String snapshotId = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        FinancialEvidenceItem comparisonClose = evidence(comparison.fullCode(), snapshotId);
        FinancialSnapshot snapshot = new FinancialSnapshot(
                primary,
                "20260813",
                "hybrid",
                List.of(comparisonClose),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                LocalDateTime.of(2026, 8, 13, 10, 0),
                List.of(new ComparisonSecuritySnapshot(
                        snapshotId, comparison, "20260813", 1, 1, "SUCCESS"
                ))
        );
        List<FinancialMetricResult> metrics = List.of(
                new FinancialMetricResult(
                        "510500.SH 收盘价",
                        new BigDecimal("6.25"),
                        "6.25",
                        "来源直接披露 ETF_CLOSE",
                        "comparison-metric-v1",
                        "OK",
                        "",
                        List.of(FinancialMetricInputNames.ETF_CLOSE)
                ),
                new FinancialMetricResult(
                        "510500.SH 单位净值",
                        null,
                        "DATA_MISSING",
                        "确定性可比指标",
                        "comparison-metric-v1",
                        "DATA_MISSING",
                        "DATA_MISSING：缺少 ETF_UNIT_NAV",
                        List.of(FinancialMetricInputNames.ETF_UNIT_NAV)
                )
        );
        InvestmentReportWriter writer = new InvestmentReportWriter((prompt, modelType) -> {
            throw new IllegalStateException("测试环境不调用 LLM");
        });

        String report = writer.write(snapshot, metrics, null, null);

        assertThat(report).contains(
                "可比证券确定性对照",
                "510500.SH",
                "快照 0123456789ab",
                "收盘价 6.25",
                "DATA_MISSING",
                "[可比 510500.SH]"
        );
    }

    private FinancialEvidenceItem evidence(String subjectCode, String snapshotId) {
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                "https://tushare.pro",
                null,
                "20260813",
                FinancialMetricInputNames.ETF_CLOSE,
                new BigDecimal("6.25"),
                new BigDecimal("6.25"),
                "ETF 收盘价 6.25",
                new BigDecimal("0.95"),
                LocalDateTime.of(2026, 8, 13, 10, 0),
                "",
                subjectCode,
                snapshotId
        );
    }
}
