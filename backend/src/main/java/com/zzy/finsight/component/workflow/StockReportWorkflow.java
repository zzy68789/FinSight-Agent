package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.component.analysis.FinancialMetricEngine;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;
import com.zzy.finsight.component.marketdata.FinancialSnapshotBuilder;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;


import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class StockReportWorkflow {
    private final StockCodeResolver stockCodeResolver;
    private final FinancialSnapshotBuilder snapshotBuilder;
    private final FinancialMetricEngine metricEngine;
    private final FinancialRiskScorer riskScorer;
    private final InvestmentReportWriter reportWriter;
    private final CitationReviewer citationReviewer;
    private final FinancialComplianceReviewer complianceReviewer;
    private final FinancialEvaluator evaluator;

    public StockReportWorkflow(
            StockCodeResolver stockCodeResolver,
            FinancialSnapshotBuilder snapshotBuilder,
            FinancialMetricEngine metricEngine,
            FinancialRiskScorer riskScorer,
            InvestmentReportWriter reportWriter,
            CitationReviewer citationReviewer,
            FinancialComplianceReviewer complianceReviewer,
            FinancialEvaluator evaluator
    ) {
        this.stockCodeResolver = stockCodeResolver;
        this.snapshotBuilder = snapshotBuilder;
        this.metricEngine = metricEngine;
        this.riskScorer = riskScorer;
        this.reportWriter = reportWriter;
        this.citationReviewer = citationReviewer;
        this.complianceReviewer = complianceReviewer;
        this.evaluator = evaluator;
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
        return riskScorer.assess(metrics, snapshot.evidenceItems());
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
        return evaluator.evaluateDefaultCase(report, snapshot, metrics);
    }
}
