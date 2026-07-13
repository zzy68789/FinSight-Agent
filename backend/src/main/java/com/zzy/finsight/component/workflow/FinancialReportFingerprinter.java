package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.MetricDefinitionCatalog;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

@Component
public class FinancialReportFingerprinter {
    private final MetricDefinitionCatalog metricCatalog;

    public FinancialReportFingerprinter(MetricDefinitionCatalog metricCatalog) {
        this.metricCatalog = metricCatalog;
    }

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
        snapshot.evidenceItems().stream()
                .sorted(Comparator
                        .comparing((FinancialEvidenceItem item) -> safe(item.sourceType()))
                        .thenComparing(item -> safe(item.sourceName()))
                        .thenComparing(item -> safe(item.metricName()))
                        .thenComparing(item -> item.asOf() == null ? "" : item.asOf().toString())
                        .thenComparing(item -> safe(item.excerpt())))
                .forEach(item -> {
                    append(canonical, item.sourceType());
                    append(canonical, item.sourceName());
                    append(canonical, item.reportPeriod());
                    append(canonical, item.metricName());
                    append(canonical, decimal(item.rawValue()));
                    append(canonical, decimal(item.normalizedValue()));
                    append(canonical, item.excerpt());
                    append(canonical, item.asOf() == null ? "" : item.asOf().toString());
                    append(canonical, item.issueCode());
                });
        return sha256(canonical.toString());
    }

    public String generationContextHash(String dataSnapshotHash) {
        return sha256(String.join("|",
                safe(dataSnapshotHash),
                metricCatalog.catalogVersion(),
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
