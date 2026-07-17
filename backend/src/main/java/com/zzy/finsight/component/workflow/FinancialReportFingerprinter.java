package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.MarketDataPoint;
import com.zzy.finsight.domain.stock.EtfDeepData;
import com.zzy.finsight.domain.stock.metric.MetricDefinitionCatalog;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.component.review.BullBearResearchAgent;
import com.zzy.finsight.component.marketdata.FinancialEvidenceValidator;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

/**
 * 计算数据快照和报告生成上下文指纹。
 */
@Component
public class FinancialReportFingerprinter {
    private final MetricDefinitionCatalog metricCatalog;

    public FinancialReportFingerprinter(MetricDefinitionCatalog metricCatalog) {
        this.metricCatalog = metricCatalog;
    }

    /** 计算金融快照的稳定摘要。 */
    public String dataSnapshotHash(FinancialSnapshot snapshot) {
        if (snapshot == null || snapshot.subject() == null) {
            throw new IllegalArgumentException("金融快照和证券信息不能为空");
        }
        StringBuilder canonical = new StringBuilder();
        StockSubject subject = snapshot.subject();
        append(canonical, subject.ticker());
        append(canonical, subject.exchange());
        append(canonical, subject.fullCode());
        append(canonical, subject.companyName());
        append(canonical, subject.industry());
        append(canonical, snapshot.reportPeriod());
        append(canonical, snapshot.searchMode());
        evidenceItems(snapshot).map(this::canonicalEvidence)
                .sorted()
                .forEach(value -> append(canonical, value));
        snapshot.marketSeries().stream()
                .map(this::canonicalMarketPoint)
                .sorted()
                .forEach(value -> append(canonical, value));
        if (snapshot.etfDeepData() != null) {
            append(canonical, canonicalEtfDeepData(snapshot.etfDeepData()));
        }
        return sha256(canonical.toString());
    }

    private Stream<FinancialEvidenceItem> evidenceItems(FinancialSnapshot snapshot) {
        return snapshot.evidenceItems() == null ? Stream.empty() : snapshot.evidenceItems().stream();
    }

    /**
     * 只纳入会影响报告正文或引用定位的业务字段，采集时间不参与缓存身份计算。
     */
    private String canonicalEvidence(FinancialEvidenceItem item) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, item.sourceType());
        append(canonical, item.sourceName());
        append(canonical, item.url());
        append(canonical, item.pageNumber() == null ? "" : item.pageNumber().toString());
        append(canonical, item.reportPeriod());
        append(canonical, item.metricName());
        append(canonical, decimal(item.rawValue()));
        append(canonical, decimal(item.normalizedValue()));
        append(canonical, item.excerpt());
        append(canonical, item.issueCode());
        return canonical.toString();
    }

    private String canonicalMarketPoint(MarketDataPoint point) {
        return String.join("|",
                safe(point.tradeDate()),
                decimal(point.open()),
                decimal(point.high()),
                decimal(point.low()),
                decimal(point.close()),
                decimal(point.previousClose()),
                decimal(point.changePercent()),
                decimal(point.volume()),
                decimal(point.amount())
        );
    }

    private String canonicalEtfDeepData(EtfDeepData data) {
        return String.join("|",
                safe(data.fundName()),
                safe(data.management()),
                safe(data.custodian()),
                safe(data.fundType()),
                safe(data.investType()),
                safe(data.benchmark()),
                safe(data.listDate()),
                decimal(data.managementFee()),
                decimal(data.custodyFee()),
                safe(data.navDate()),
                decimal(data.unitNav()),
                decimal(data.accumulatedNav()),
                decimal(data.totalNetAsset()),
                decimal(data.premiumDiscountRate())
        );
    }

    /** 结合指标版本计算报告生成上下文摘要。 */
    public String generationContextHash(String dataSnapshotHash) {
        return sha256(String.join("|",
                safe(dataSnapshotHash),
                FinancialEvidenceValidator.POLICY_VERSION,
                metricCatalog.catalogVersion(),
                BullBearResearchAgent.POLICY_VERSION,
                InvestmentReportWriter.WRITER_VERSION,
                CitationReviewer.POLICY_VERSION,
                FinancialComplianceReviewer.POLICY_VERSION,
                FinancialEvaluator.POLICY_VERSION
        ));
    }

    private void append(StringBuilder builder, String value) {
        String normalized = safe(value).replaceAll("\\s+", " ").trim();
        builder.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
