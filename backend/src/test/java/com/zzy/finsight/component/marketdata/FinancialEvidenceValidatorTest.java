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

    @Test
    void marksNavigationQuotePagesAndDuplicateUrlsAsLowQualityOrDuplicate() {
        FinancialEvidenceItem navigation = publicEvidence(
                "https://quote.example.com/600519",
                "概览 行情首页 财务分析指标 业绩预告 业绩快报 大宗交易 融资融券 股东统计 基金持仓 财报全文"
        );
        FinancialEvidenceItem duplicate = publicEvidence(
                "https://quote.example.com/600519",
                "公司发布正式公告，正文包含经营变化、风险提示、报告日期和可复核的数据来源。"
        );

        FinancialSnapshot validated = validator.validate(snapshot(List.of(navigation, duplicate)));

        assertThat(validated.evidenceItems()).extracting(FinancialEvidenceItem::issueCode)
                .containsExactly(
                        FinancialEvidenceIssueCodes.LOW_QUALITY_CONTENT,
                        FinancialEvidenceIssueCodes.DUPLICATE_PERIOD
                );
    }

    @Test
    void keepsSubstantivePublicNewsEffective() {
        FinancialEvidenceItem news = publicEvidence(
                "https://www.sse.com.cn/news/600519",
                "公司发布经营公告，正文说明报告期收入变化、渠道调整、现金流影响及相关风险，内容可回到公告原文复核。"
        );

        FinancialSnapshot validated = validator.validate(snapshot(List.of(news)));

        assertThat(validated.evidenceItems().get(0).effective()).isTrue();
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

    private FinancialEvidenceItem publicEvidence(String url, String excerpt) {
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                "公开网页",
                url,
                null,
                "latest",
                "NEWS_SUMMARY",
                null,
                null,
                excerpt,
                new BigDecimal("0.80"),
                LocalDateTime.of(2026, 7, 13, 20, 0),
                ""
        );
    }
}
