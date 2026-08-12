package com.zzy.finsight.agent.memory;

import com.zzy.finsight.agent.planning.ResearchPlan;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.quality.QualityGateDecision;
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
    private QualityGateDecision qualityGateDecision;
    private int evidenceRecoveryCount;
    private EvidenceRecoveryDirective evidenceRecoveryDirective;
    private Set<String> executedCallHashes = new LinkedHashSet<>();
    private Set<String> completedTools = new LinkedHashSet<>();
    private Set<String> reexecutionAllowedTools = new LinkedHashSet<>();
    private List<ToolInvocation> toolInvocationHistory = new ArrayList<>();
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

    public QualityGateDecision getQualityGateDecision() {
        return qualityGateDecision;
    }

    public void setQualityGateDecision(QualityGateDecision qualityGateDecision) {
        this.qualityGateDecision = qualityGateDecision;
    }

    public int getEvidenceRecoveryCount() {
        return evidenceRecoveryCount;
    }

    public void setEvidenceRecoveryCount(int evidenceRecoveryCount) {
        this.evidenceRecoveryCount = Math.max(0, evidenceRecoveryCount);
    }

    public EvidenceRecoveryDirective getEvidenceRecoveryDirective() {
        return evidenceRecoveryDirective;
    }

    public void setEvidenceRecoveryDirective(EvidenceRecoveryDirective evidenceRecoveryDirective) {
        this.evidenceRecoveryDirective = evidenceRecoveryDirective;
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

    public Set<String> getReexecutionAllowedTools() {
        return reexecutionAllowedTools == null ? Set.of() : Set.copyOf(reexecutionAllowedTools);
    }

    public void setReexecutionAllowedTools(Set<String> reexecutionAllowedTools) {
        this.reexecutionAllowedTools = new LinkedHashSet<>(
                reexecutionAllowedTools == null ? Set.of() : reexecutionAllowedTools
        );
    }

    public List<ToolInvocation> getToolInvocationHistory() {
        return toolInvocationHistory == null ? List.of() : List.copyOf(toolInvocationHistory);
    }

    public void setToolInvocationHistory(List<ToolInvocation> toolInvocationHistory) {
        this.toolInvocationHistory = new ArrayList<>(
                toolInvocationHistory == null ? List.of() : toolInvocationHistory
        );
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

    /** 记录一次成功或可解释失败的工具调用与观察。 */
    public void recordObservation(ToolInvocation invocation, String callHash, String summary) {
        String toolName = invocation == null ? "" : invocation.toolName();
        completedTools.add(toolName);
        executedCallHashes.add(callHash);
        reexecutionAllowedTools.remove(toolName);
        if (invocation != null) {
            toolInvocationHistory.add(invocation);
            if (toolInvocationHistory.size() > 30) {
                toolInvocationHistory.remove(0);
            }
        }
        observations.add(toolName + "：" + (summary == null ? "" : summary));
        if (observations.size() > 20) {
            observations.remove(0);
        }
    }

    /** 根据质量门禁创建一轮需要差异化调用证据工具的恢复任务。 */
    public void beginEvidenceRecovery(QualityGateDecision decision) {
        evidenceRecoveryCount++;
        long effectiveEvidenceCount = snapshot == null ? 0L : snapshot.evidenceItems().stream()
                .filter(com.zzy.finsight.domain.stock.FinancialEvidenceItem::effective)
                .count();
        List<String> issueCodes = decision == null ? List.of() : decision.issues().stream()
                .map(issue -> issue.code())
                .distinct()
                .toList();
        evidenceRecoveryDirective = EvidenceRecoveryDirective.pending(
                evidenceRecoveryCount,
                issueCodes,
                decision == null ? "" : decision.summary(),
                effectiveEvidenceCount
        );
    }

    /** 记录补证据工具结果，并在新增有效证据后解除证据调用约束。 */
    public void recordEvidenceRecoveryAttempt(ToolInvocation invocation, long newEffectiveEvidenceCount) {
        if (evidenceRecoveryDirective != null) {
            evidenceRecoveryDirective = evidenceRecoveryDirective.recordAttempt(
                    invocation, newEffectiveEvidenceCount
            );
        }
    }

    /** 返回当前是否必须优先执行新的证据工具调用。 */
    public boolean hasPendingEvidenceRecovery() {
        return evidenceRecoveryDirective != null && evidenceRecoveryDirective.pending();
    }

    /** 新证据进入快照后清除派生结果，并允许相关确定性工具各重执行一次。 */
    public void invalidateDerivedResultsAfterEvidenceChange() {
        setMetrics(List.of());
        setRiskAssessment(null);
        setBullBearResearch(null);
        for (String toolName : List.of(
                "calculate_financial_metrics",
                "assess_financial_risk",
                "build_bull_bear_cases",
                "check_evidence_coverage"
        )) {
            completedTools.remove(toolName);
            reexecutionAllowedTools.add(toolName);
        }
    }

    /** 返回指定工具是否因输入状态失效而获得一次受控重执行权限。 */
    public boolean isToolReexecutionAllowed(String toolName) {
        return reexecutionAllowedTools.contains(toolName);
    }

    /** 仅为门禁要求的确定性重算释放指定工具调用，其他重复调用仍保持禁止。 */
    public void allowDeterministicReexecution(String toolName, String callHash) {
        if (toolName != null) {
            completedTools.remove(toolName);
            reexecutionAllowedTools.add(toolName);
        }
        if (callHash != null) {
            executedCallHashes.remove(callHash);
        }
    }
}
