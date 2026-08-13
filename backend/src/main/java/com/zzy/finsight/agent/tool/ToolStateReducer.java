package com.zzy.finsight.agent.tool;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.EvidenceMemory;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.ComparisonSecuritySnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单线程应用类型化工具结果，是工具修改 AgentState 的唯一入口。
 */
@Component
public class ToolStateReducer {
    private final EvidenceMemory evidenceMemory;

    public ToolStateReducer(EvidenceMemory evidenceMemory) {
        this.evidenceMemory = evidenceMemory;
    }

    /** 应用一次工具结果，并返回包含实际新增证据的规范化结果。 */
    public ToolResult apply(AgentState state, String toolName, long durationMs, ToolResult result) {
        if (result == null || "FAILED".equals(result.status())) {
            return result;
        }
        ToolPayload payload = result.payload();
        if (payload instanceof ToolPayload.Security security) {
            state.setSubject(security.subject());
            if (state.getSnapshot() == null) {
                state.setSnapshot(new FinancialSnapshot(
                        security.subject(),
                        state.getRequest().getAsOfDate().toString(),
                        state.getRequest().getSearchMode(),
                        List.of(),
                        LocalDateTime.now()
                ));
            }
        } else if (payload instanceof ToolPayload.ComparisonEvidence comparisonEvidence) {
            applyComparisonSnapshots(state, comparisonEvidence.snapshots());
            List<FinancialEvidenceItem> added = evidenceMemory.merge(
                    state,
                    toolName,
                    FinancialDataCollection.evidenceOnly(comparisonEvidence.evidence()),
                    durationMs,
                    result.status(),
                    result.summary()
            );
            return new ToolResult(
                    result.status(),
                    result.summary(),
                    comparisonEvidence.withEvidence(added),
                    added,
                    result.errorCode(),
                    result.retryable()
            );
        } else if (payload instanceof ToolPayload.Evidence evidence) {
            List<FinancialEvidenceItem> added = evidenceMemory.merge(
                    state,
                    toolName,
                    new FinancialDataCollection(
                            evidence.evidence(),
                            evidence.retrievalResult(),
                            evidence.marketSeries(),
                            evidence.etfDeepData()
                    ),
                    durationMs,
                    result.status(),
                    result.summary()
            );
            return new ToolResult(
                    result.status(),
                    result.summary(),
                    evidence.withEvidence(added),
                    added,
                    result.errorCode(),
                    result.retryable()
            );
        } else if (payload instanceof ToolPayload.Metrics metrics) {
            state.setMetrics(metrics.metrics());
        } else if (payload instanceof ToolPayload.Risk risk) {
            state.setRiskAssessment(risk.riskAssessment());
        } else if (payload instanceof ToolPayload.BullBear research) {
            state.setBullBearResearch(research.research());
        }
        return result;
    }

    /** 把可比证券快照索引合并到主快照，但不把其行情序列混入主证券行情。 */
    private void applyComparisonSnapshots(
            AgentState state,
            List<ComparisonSecuritySnapshot> comparisonSnapshots
    ) {
        FinancialSnapshot current = state.getSnapshot();
        if (current == null) {
            throw new IllegalStateException("可比证券采集前必须先建立主证券快照");
        }
        java.util.Map<String, ComparisonSecuritySnapshot> snapshots = new java.util.LinkedHashMap<>();
        current.comparisonSnapshots().forEach(item -> snapshots.put(item.subject().fullCode(), item));
        (comparisonSnapshots == null ? List.<ComparisonSecuritySnapshot>of() : comparisonSnapshots)
                .forEach(item -> snapshots.put(item.subject().fullCode(), item));
        state.setSnapshot(new FinancialSnapshot(
                current.subject(),
                current.reportPeriod(),
                current.searchMode(),
                current.evidenceItems(),
                current.evidenceArbitrations(),
                current.stageResults(),
                current.retrievalResults(),
                current.marketSeries(),
                current.etfDeepData(),
                current.createdAt(),
                List.copyOf(snapshots.values())
        ));
    }
}
