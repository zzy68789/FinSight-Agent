package com.zzy.finsight.agent.memory;

import com.zzy.finsight.agent.planning.ResearchPlan;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.agent.ResearchRunRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 保存可序列化、可恢复的 Research Agent 运行状态。
 */
public class AgentState {
    private long taskId;
    private String threadId;
    private ResearchRunRequest request;
    private ResearchPlan plan;
    private String phase = "CREATED";
    private int turnNo;
    private int toolCallCount;
    private int replanCount;
    private int reportRewriteCount;
    private int consecutiveNoNewEvidenceTurns;
    private Long snapshotId;
    private StockSubject subject;
    private FinancialSnapshot snapshot;
    private List<FinancialMetricResult> metrics = new ArrayList<>();
    private FinancialRiskAssessment riskAssessment;
    private BullBearResearchResult bullBearResearch;
    private String finalReport = "";
    private CitationReviewResult citationReview;
    private FinancialComplianceReviewResult complianceReview;
    private FinancialEvaluationResult evaluation;
    private Set<String> executedCallHashes = new LinkedHashSet<>();
    private Set<String> completedTools = new LinkedHashSet<>();
    private List<String> observations = new ArrayList<>();
    private String lastReviewReason = "";
    private String stopReason = "";
    private String contextHash = "";
    private boolean plannerDegraded;

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public ResearchRunRequest getRequest() {
        return request;
    }

    public void setRequest(ResearchRunRequest request) {
        this.request = request;
    }

    public ResearchPlan getPlan() {
        return plan;
    }

    public void setPlan(ResearchPlan plan) {
        this.plan = plan;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public int getTurnNo() {
        return turnNo;
    }

    public void setTurnNo(int turnNo) {
        this.turnNo = turnNo;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public int getReplanCount() {
        return replanCount;
    }

    public void setReplanCount(int replanCount) {
        this.replanCount = replanCount;
    }

    public int getReportRewriteCount() {
        return reportRewriteCount;
    }

    public void setReportRewriteCount(int reportRewriteCount) {
        this.reportRewriteCount = reportRewriteCount;
    }

    public int getConsecutiveNoNewEvidenceTurns() {
        return consecutiveNoNewEvidenceTurns;
    }

    public void setConsecutiveNoNewEvidenceTurns(int consecutiveNoNewEvidenceTurns) {
        this.consecutiveNoNewEvidenceTurns = Math.max(0, consecutiveNoNewEvidenceTurns);
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public StockSubject getSubject() {
        return subject;
    }

    public void setSubject(StockSubject subject) {
        this.subject = subject;
    }

    public FinancialSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(FinancialSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public List<FinancialMetricResult> getMetrics() {
        return metrics == null ? List.of() : List.copyOf(metrics);
    }

    public void setMetrics(List<FinancialMetricResult> metrics) {
        this.metrics = new ArrayList<>(metrics == null ? List.of() : metrics);
    }

    public FinancialRiskAssessment getRiskAssessment() {
        return riskAssessment;
    }

    public void setRiskAssessment(FinancialRiskAssessment riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    public BullBearResearchResult getBullBearResearch() {
        return bullBearResearch;
    }

    public void setBullBearResearch(BullBearResearchResult bullBearResearch) {
        this.bullBearResearch = bullBearResearch;
    }

    public String getFinalReport() {
        return finalReport;
    }

    public void setFinalReport(String finalReport) {
        this.finalReport = finalReport == null ? "" : finalReport;
    }

    public CitationReviewResult getCitationReview() {
        return citationReview;
    }

    public void setCitationReview(CitationReviewResult citationReview) {
        this.citationReview = citationReview;
    }

    public FinancialComplianceReviewResult getComplianceReview() {
        return complianceReview;
    }

    public void setComplianceReview(FinancialComplianceReviewResult complianceReview) {
        this.complianceReview = complianceReview;
    }

    public FinancialEvaluationResult getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(FinancialEvaluationResult evaluation) {
        this.evaluation = evaluation;
    }

    public Set<String> getExecutedCallHashes() {
        return executedCallHashes == null ? Set.of() : Set.copyOf(executedCallHashes);
    }

    public void setExecutedCallHashes(Set<String> executedCallHashes) {
        this.executedCallHashes = new LinkedHashSet<>(executedCallHashes == null ? Set.of() : executedCallHashes);
    }

    public Set<String> getCompletedTools() {
        return completedTools == null ? Set.of() : Set.copyOf(completedTools);
    }

    public void setCompletedTools(Set<String> completedTools) {
        this.completedTools = new LinkedHashSet<>(completedTools == null ? Set.of() : completedTools);
    }

    public List<String> getObservations() {
        return observations == null ? List.of() : List.copyOf(observations);
    }

    public void setObservations(List<String> observations) {
        this.observations = new ArrayList<>(observations == null ? List.of() : observations);
    }

    public String getLastReviewReason() {
        return lastReviewReason;
    }

    public void setLastReviewReason(String lastReviewReason) {
        this.lastReviewReason = lastReviewReason == null ? "" : lastReviewReason;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason == null ? "" : stopReason;
    }

    public String getContextHash() {
        return contextHash;
    }

    public void setContextHash(String contextHash) {
        this.contextHash = contextHash == null ? "" : contextHash;
    }

    public boolean isPlannerDegraded() {
        return plannerDegraded;
    }

    public void setPlannerDegraded(boolean plannerDegraded) {
        this.plannerDegraded = plannerDegraded;
    }

    /** 记录一次成功或可解释失败的工具观察。 */
    public void recordObservation(String toolName, String callHash, String summary) {
        completedTools.add(toolName);
        executedCallHashes.add(callHash);
        observations.add(toolName + "：" + (summary == null ? "" : summary));
        if (observations.size() > 20) {
            observations.remove(0);
        }
    }
}
