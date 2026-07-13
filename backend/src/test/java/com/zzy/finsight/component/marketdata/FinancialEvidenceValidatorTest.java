package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialEvidenceValidatorTest {
    private final FinancialEvidenceValidator validator = new FinancialEvidenceValidator(
            Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void marksPriorRevenueWhenPeriodIsNotPreviousYear() {
        FinancialSnapshot validated = validator.validate(snapshot(List.of(
                evidence(FinancialMetricInputNames.OPERATING_REVENUE, "20260331", "539.09", ""),
                evidence(FinancialMetricInputNames.OPERATING_REVENUE_PRIOR, "20260331", "539.09", "")
        )));

        FinancialEvidenceItem prior = validated.evidenceItems().get(1);
        assertThat(prior.issueCode()).isEqualTo(FinancialEvidenceIssueCodes.PRIOR_PERIOD_MISMATCH);
        assertThat(prior.effective()).isFalse();
    }

    @Test
    void marksInvalidBalanceRelationAndLowQualityNews() {
        FinancialSnapshot validated = validator.validate(snapshot(List.of(
                evidence(FinancialMetricInputNames.TOTAL_ASSETS, "20260331", "100", ""),
                evidence(FinancialMetricInputNames.TOTAL_LIABILITIES, "20260331", "120", ""),
                evidence("NEWS_SUMMARY", "latest", null, "[登录] 公司简介 @open@")
        )));

        assertThat(validated.evidenceItems()).extracting(FinancialEvidenceItem::issueCode)
                .containsExactly(
                        FinancialEvidenceIssueCodes.INVALID_FINANCIAL_RELATION,
                        FinancialEvidenceIssueCodes.INVALID_FINANCIAL_RELATION,
                        FinancialEvidenceIssueCodes.LOW_QUALITY_CONTENT
                );
    }

    private FinancialSnapshot snapshot(List<FinancialEvidenceItem> items) {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "latest",
                "hybrid",
                items,
                LocalDateTime.of(2026, 7, 13, 20, 0)
        );
    }

    private FinancialEvidenceItem evidence(String metricName, String period, String value, String excerpt) {
        BigDecimal number = value == null ? null : new BigDecimal(value);
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                "https://tushare.pro",
                null,
                period,
                metricName,
                number,
                number,
                excerpt,
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 7, 13, 20, 0),
                ""
        );
    }
}
