package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceArbitration;
import com.zzy.finsight.domain.stock.FinancialEvidenceArbitrationCandidate;
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
                "公司发布经营公告，正文说明报告期收入变化、渠道调整、现金流影响及相关风险，内容可回到公告原文复核，并列明公告日期、适用范围和后续信息披露安排。"
        );

        FinancialSnapshot validated = validator.validate(snapshot(List.of(news)));

        assertThat(validated.evidenceItems().get(0).effective()).isTrue();
    }

    @Test
    void selectsHigherPrioritySourceAndRecordsStructuredResolution() {
        FinancialSnapshot validated = validator.validate(snapshot(List.of(
                evidence("UPLOADED_REPORT", "上传年报", FinancialMetricInputNames.OPERATING_REVENUE,
                        "20260331", "100"),
                evidence("AUTHORIZED_MARKET", "TuShare Pro", FinancialMetricInputNames.OPERATING_REVENUE,
                        "20260331", "120")
        )));

        assertThat(validated.evidenceItems().get(0).issueCode())
                .isEqualTo(FinancialEvidenceIssueCodes.SOURCE_CONFLICT_REJECTED);
        assertThat(validated.evidenceItems().get(1).effective()).isTrue();
        assertThat(validated.evidenceArbitrations()).singleElement().satisfies(arbitration -> {
            assertThat(arbitration.status()).isEqualTo(FinancialEvidenceArbitration.Status.RESOLVED);
            assertThat(arbitration.selectedSourceName()).isEqualTo("TuShare Pro");
            assertThat(arbitration.selectedValue()).isEqualByComparingTo("120");
            assertThat(arbitration.reason()).contains("唯一最高优先级来源胜出", "优先级 100");
            assertThat(arbitration.candidates()).extracting(FinancialEvidenceArbitrationCandidate::decision)
                    .containsExactly(
                            FinancialEvidenceArbitrationCandidate.Decision.SELECTED,
                            FinancialEvidenceArbitrationCandidate.Decision.REJECTED
                    );
        });
    }

    @Test
    void failsClosedWhenHighestPrioritySourcesStillConflict() {
        FinancialSnapshot validated = validator.validate(snapshot(List.of(
                evidence("UPLOADED_REPORT", "来源A", FinancialMetricInputNames.NET_PROFIT,
                        "20260331", "100"),
                evidence("UPLOADED_REPORT", "来源B", FinancialMetricInputNames.NET_PROFIT,
                        "20260331", "120")
        )));

        assertThat(validated.evidenceItems()).extracting(FinancialEvidenceItem::issueCode)
                .containsOnly(FinancialEvidenceIssueCodes.EVIDENCE_CONFLICT);
        assertThat(validated.evidenceItems()).noneMatch(FinancialEvidenceItem::effective);
        assertThat(validated.evidenceArbitrations()).singleElement()
                .satisfies(arbitration -> {
                    assertThat(arbitration.status()).isEqualTo(FinancialEvidenceArbitration.Status.CONFLICT);
                    assertThat(arbitration.selectedValue()).isNull();
                    assertThat(arbitration.reason()).contains("无法安全选择");
                });
    }

    @Test
    void reArbitratesPreviouslyConflictingEvidenceAfterHigherPriorityEvidenceArrives() {
        FinancialSnapshot conflicted = validator.validate(snapshot(List.of(
                evidence("UPLOADED_REPORT", "来源A", FinancialMetricInputNames.NET_PROFIT,
                        "20260331", "100"),
                evidence("UPLOADED_REPORT", "来源B", FinancialMetricInputNames.NET_PROFIT,
                        "20260331", "120")
        )));
        FinancialSnapshot recovered = validator.validate(snapshot(List.of(
                conflicted.evidenceItems().get(0),
                conflicted.evidenceItems().get(1),
                evidence("AUTHORIZED_MARKET", "TuShare Pro", FinancialMetricInputNames.NET_PROFIT,
                        "20260331", "120")
        )));

        assertThat(recovered.evidenceItems()).extracting(FinancialEvidenceItem::issueCode)
                .containsExactly(
                        FinancialEvidenceIssueCodes.SOURCE_CONFLICT_REJECTED,
                        FinancialEvidenceIssueCodes.SOURCE_CORROBORATING,
                        ""
                );
        assertThat(recovered.evidenceArbitrations()).singleElement().satisfies(arbitration -> {
            assertThat(arbitration.status()).isEqualTo(FinancialEvidenceArbitration.Status.RESOLVED);
            assertThat(arbitration.selectedSourceName()).isEqualTo("TuShare Pro");
        });
    }

    @Test
    void recordsConsistentLowerPriorityValueAsCorroboratingOnly() {
        FinancialSnapshot validated = validator.validate(snapshot(List.of(
                evidence("UPLOADED_REPORT", "上传年报", FinancialMetricInputNames.TOTAL_ASSETS,
                        "20260331", "120.50"),
                evidence("AUTHORIZED_MARKET", "TuShare Pro", FinancialMetricInputNames.TOTAL_ASSETS,
                        "20260331", "120.00")
        )));

        assertThat(validated.evidenceItems().get(0).issueCode())
                .isEqualTo(FinancialEvidenceIssueCodes.SOURCE_CORROBORATING);
        assertThat(validated.evidenceArbitrations()).singleElement()
                .extracting(FinancialEvidenceArbitration::status)
                .isEqualTo(FinancialEvidenceArbitration.Status.CONSISTENT);
    }

    @Test
    void prioritizesOfficialDisclosureAboveAuthorizedAndUploadedSources() {
        FinancialEvidenceSourcePriorityPolicy policy = new FinancialEvidenceSourcePriorityPolicy();
        FinancialEvidenceItem official = new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                "上海证券交易所公告",
                "https://www.sse.com.cn/disclosure/listedinfo/announcement/",
                null,
                "20260331",
                FinancialMetricInputNames.OPERATING_REVENUE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "营业收入 1 亿元",
                BigDecimal.ONE,
                LocalDateTime.of(2026, 7, 13, 20, 0),
                ""
        );
        FinancialEvidenceItem misleadingTitle = new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                "上海证券交易所公告转载",
                "https://example.com/copied-announcement",
                null,
                "20260331",
                FinancialMetricInputNames.OPERATING_REVENUE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "转载营业收入 1 亿元",
                BigDecimal.ONE,
                LocalDateTime.of(2026, 7, 13, 20, 0),
                ""
        );

        assertThat(policy.priority(official)).isEqualTo(110);
        assertThat(policy.priority(misleadingTitle)).isEqualTo(60);
        assertThat(policy.priority(evidence(
                "AUTHORIZED_MARKET", "TuShare Pro", FinancialMetricInputNames.OPERATING_REVENUE,
                "20260331", "1"
        ))).isEqualTo(100);
        assertThat(policy.priority(evidence(
                "UPLOADED_REPORT", "上传报告", FinancialMetricInputNames.OPERATING_REVENUE,
                "20260331", "1"
        ))).isEqualTo(80);
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
        return evidence("AUTHORIZED_MARKET", "TuShare Pro", metricName, period, value, excerpt);
    }

    private FinancialEvidenceItem evidence(
            String sourceType,
            String sourceName,
            String metricName,
            String period,
            String value
    ) {
        return evidence(sourceType, sourceName, metricName, period, value, metricName + "=" + value);
    }

    private FinancialEvidenceItem evidence(
            String sourceType,
            String sourceName,
            String metricName,
            String period,
            String value,
            String excerpt
    ) {
        BigDecimal number = value == null ? null : new BigDecimal(value);
        return new FinancialEvidenceItem(
                sourceType,
                sourceName,
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
