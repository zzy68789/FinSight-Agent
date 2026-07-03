package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockReportWorkflow {
    private final StockCodeResolver stockCodeResolver;
    private final FinancialSnapshotBuilder snapshotBuilder;
    private final FinancialMetricEngine metricEngine;
    private final InvestmentReportWriter reportWriter;
    private final CitationReviewer citationReviewer;

    public StockReportWorkflow(
            StockCodeResolver stockCodeResolver,
            FinancialSnapshotBuilder snapshotBuilder,
            FinancialMetricEngine metricEngine,
            InvestmentReportWriter reportWriter,
            CitationReviewer citationReviewer
    ) {
        this.stockCodeResolver = stockCodeResolver;
        this.snapshotBuilder = snapshotBuilder;
        this.metricEngine = metricEngine;
        this.reportWriter = reportWriter;
        this.citationReviewer = citationReviewer;
    }

    public StockSubject resolve(StockReportRequest request) {
        return stockCodeResolver.resolve(request.getTicker());
    }

    public FinancialSnapshot snapshot(StockSubject subject, StockReportRequest request) {
        return snapshotBuilder.build(subject, request.getReportPeriod(), request.getSearchMode());
    }

    public List<FinancialMetricResult> metrics(FinancialSnapshot snapshot) {
        return metricEngine.compute(snapshot);
    }

    public String write(FinancialSnapshot snapshot, List<FinancialMetricResult> metrics, CitationReviewResult previousReview) {
        return reportWriter.write(snapshot, metrics, previousReview);
    }

    public CitationReviewResult review(String report, FinancialSnapshot snapshot, List<FinancialMetricResult> metrics) {
        return citationReviewer.review(report, snapshot, metrics);
    }
}
