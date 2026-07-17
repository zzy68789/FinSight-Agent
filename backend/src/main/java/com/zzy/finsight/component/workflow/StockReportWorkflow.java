package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.component.analysis.FinancialMetricEngine;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;
import com.zzy.finsight.component.marketdata.FinancialSnapshotBuilder;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.component.review.BullBearResearchAgent;


import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 编排证券解析、指标、审查和评测组件。
 */
@Component
public class StockReportWorkflow {
    private final StockCodeResolver stockCodeResolver;
    private final FinancialSnapshotBuilder snapshotBuilder;
    private final FinancialMetricEngine metricEngine;
    private final FinancialRiskScorer riskScorer;
    private final BullBearResearchAgent bullBearResearchAgent;
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
        this(stockCodeResolver, snapshotBuilder, metricEngine, riskScorer, new BullBearResearchAgent(),
                reportWriter, citationReviewer, complianceReviewer, evaluator);
    }

    @Autowired
    public StockReportWorkflow(
            StockCodeResolver stockCodeResolver,
            FinancialSnapshotBuilder snapshotBuilder,
            FinancialMetricEngine metricEngine,
            FinancialRiskScorer riskScorer,
            BullBearResearchAgent bullBearResearchAgent,
            InvestmentReportWriter reportWriter,
            CitationReviewer citationReviewer,
            FinancialComplianceReviewer complianceReviewer,
            FinancialEvaluator evaluator
    ) {
        this.stockCodeResolver = stockCodeResolver;
        this.snapshotBuilder = snapshotBuilder;
        this.metricEngine = metricEngine;
        this.riskScorer = riskScorer;
        this.bullBearResearchAgent = bullBearResearchAgent;
        this.reportWriter = reportWriter;
        this.citationReviewer = citationReviewer;
        this.complianceReviewer = complianceReviewer;
        this.evaluator = evaluator;
    }

    /** 解析请求中的证券代码或名称。 */
    public StockSubject resolve(StockReportRequest request) {
        return stockCodeResolver.resolve(request.getTicker());
    }

    /** 采集并冻结本次报告使用的金融数据。 */
    public FinancialSnapshot snapshot(long ownerId, StockSubject subject, StockReportRequest request) {
        return snapshotBuilder.build(ownerId, subject, request.getReportPeriod(), request.getSearchMode());
    }

    /** 计算金融快照对应的确定性指标。 */
    public List<FinancialMetricResult> metrics(FinancialSnapshot snapshot) {
        return metricEngine.compute(snapshot);
    }

    /** 基于指标与证据评估金融风险。 */
    public FinancialRiskAssessment riskAssessment(List<FinancialMetricResult> metrics, FinancialSnapshot snapshot) {
        return riskScorer.assess(metrics, snapshot.evidenceItems());
    }

    /** 让多头与空头研究角色基于同一证据快照形成条件化论据。 */
    public BullBearResearchResult bullBearResearch(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment
    ) {
        return bullBearResearchAgent.analyze(snapshot, metrics, riskAssessment);
    }

    /** 生成带证据引用的投研报告正文。 */
    public String write(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment,
            CitationReviewResult previousReview
    ) {
        return reportWriter.write(snapshot, metrics, riskAssessment, previousReview);
    }

    /** 将多空研究结果合并到报告草稿后生成带证据引用的正文。 */
    public String write(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment,
            BullBearResearchResult bullBearResearch,
            CitationReviewResult previousReview
    ) {
        return reportWriter.write(snapshot, metrics, riskAssessment, bullBearResearch, previousReview);
    }

    /** 审查报告中的引用和指标一致性。 */
    public CitationReviewResult review(String report, FinancialSnapshot snapshot, List<FinancialMetricResult> metrics) {
        return citationReviewer.review(report, snapshot, metrics);
    }

    /** 审查报告的金融合规表达。 */
    public FinancialComplianceReviewResult compliance(String report, CitationReviewResult citationReview) {
        return complianceReviewer.review(report, citationReview);
    }

    /** 对最终报告执行不依赖证券白名单的线上质量门禁。 */
    public FinancialEvaluationResult evaluation(
            String report,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        return evaluator.evaluateOnline(report, snapshot, metrics);
    }
}
