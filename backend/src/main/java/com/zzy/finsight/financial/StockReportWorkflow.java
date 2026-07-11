package com.zzy.finsight.financial;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class StockReportWorkflow {
    private final StockCodeResolver stockCodeResolver;
    private final FinancialSnapshotBuilder snapshotBuilder;
    private final FinancialMetricEngine metricEngine;
    private final FinancialRiskScoringService riskScoringService;
    private final InvestmentReportWriter reportWriter;
    private final CitationReviewer citationReviewer;
    private final FinancialComplianceReviewer complianceReviewer;
    private final FinancialEvaluationService evaluationService;

    public StockReportWorkflow(
            StockCodeResolver stockCodeResolver,
            FinancialSnapshotBuilder snapshotBuilder,
            FinancialMetricEngine metricEngine,
            FinancialRiskScoringService riskScoringService,
            InvestmentReportWriter reportWriter,
            CitationReviewer citationReviewer,
            FinancialComplianceReviewer complianceReviewer,
            FinancialEvaluationService evaluationService
    ) {
        this.stockCodeResolver = stockCodeResolver;
        this.snapshotBuilder = snapshotBuilder;
        this.metricEngine = metricEngine;
        this.riskScoringService = riskScoringService;
        this.reportWriter = reportWriter;
        this.citationReviewer = citationReviewer;
        this.complianceReviewer = complianceReviewer;
        this.evaluationService = evaluationService;
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

    public FinancialRiskAssessment riskAssessment(List<FinancialMetricResult> metrics, FinancialSnapshot snapshot) {
        return riskScoringService.assess(metrics, snapshot.evidenceItems());
    }

    public String write(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment,
            CitationReviewResult previousReview
    ) {
        return reportWriter.write(snapshot, metrics, riskAssessment, previousReview);
    }

    public CitationReviewResult review(String report, FinancialSnapshot snapshot, List<FinancialMetricResult> metrics) {
        return citationReviewer.review(report, snapshot, metrics);
    }

    public FinancialComplianceReviewResult compliance(String report, CitationReviewResult citationReview) {
        return complianceReviewer.review(report, citationReview);
    }

    public Optional<FinancialEvaluationResult> evaluation(
            String report,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        return evaluationService.evaluateDefaultCase(report, snapshot, metrics);
    }
}
