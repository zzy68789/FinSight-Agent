package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.domain.stock.ComparisonSecuritySnapshot;
import com.zzy.finsight.domain.stock.EtfDeepData;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.MarketDataPoint;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.rag.RagRetrievalResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 定义工具结果的封闭类型集合，持久化时写入 payloadType 以支持可靠恢复。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "payloadType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ToolPayload.Empty.class, name = "empty"),
        @JsonSubTypes.Type(value = ToolPayload.Security.class, name = "security"),
        @JsonSubTypes.Type(value = ToolPayload.Evidence.class, name = "evidence"),
        @JsonSubTypes.Type(value = ToolPayload.ComparisonEvidence.class, name = "comparison_evidence"),
        @JsonSubTypes.Type(value = ToolPayload.Metrics.class, name = "metrics"),
        @JsonSubTypes.Type(value = ToolPayload.Risk.class, name = "risk"),
        @JsonSubTypes.Type(value = ToolPayload.BullBear.class, name = "bull_bear"),
        @JsonSubTypes.Type(value = ToolPayload.Coverage.class, name = "coverage")
})
public sealed interface ToolPayload permits
        ToolPayload.Empty,
        ToolPayload.Security,
        ToolPayload.Evidence,
        ToolPayload.ComparisonEvidence,
        ToolPayload.Metrics,
        ToolPayload.Risk,
        ToolPayload.BullBear,
        ToolPayload.Coverage {

    /** 表示失败结果或无需附加数据的成功结果。 */
    record Empty() implements ToolPayload {
    }

    /** @param subject 已解析证券主体。 @param disclaimer 固定合规声明。 */
    record Security(StockSubject subject, String disclaimer) implements ToolPayload {
        public Security {
            disclaimer = disclaimer == null ? "" : disclaimer;
        }
    }

    /**
     * @param provider 数据来源名称。
     * @param query 实际公开检索问题。
     * @param evidence 本次工具返回或归约后新增的证据。
     * @param retrievalResult 检索轨迹。
     * @param marketSeries 行情序列。
     * @param etfDeepData ETF深度数据。
     * @param evidenceCount 证据数量。
     * @param effectiveCount 有效证据数量。
     */
    record Evidence(
            String provider,
            String query,
            List<FinancialEvidenceItem> evidence,
            RagRetrievalResult retrievalResult,
            List<MarketDataPoint> marketSeries,
            EtfDeepData etfDeepData,
            int evidenceCount,
            long effectiveCount
    ) implements ToolPayload {
        public Evidence {
            provider = provider == null ? "" : provider;
            query = query == null ? "" : query;
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            marketSeries = marketSeries == null ? List.of() : List.copyOf(marketSeries);
            evidenceCount = Math.max(0, evidenceCount);
            effectiveCount = Math.max(0L, effectiveCount);
        }

        /** 返回使用归约后新增证据更新的负载。 */
        public Evidence withEvidence(List<FinancialEvidenceItem> added) {
            List<FinancialEvidenceItem> values = added == null ? List.of() : List.copyOf(added);
            return new Evidence(
                    provider,
                    query,
                    values,
                    retrievalResult,
                    marketSeries,
                    etfDeepData,
                    values.size(),
                    values.stream().filter(FinancialEvidenceItem::effective).count()
            );
        }
    }

    /**
     * @param evidence 已标注证券代码和可比快照标识的证据。
     * @param snapshots 每个可比证券的独立快照索引。
     */
    record ComparisonEvidence(
            List<FinancialEvidenceItem> evidence,
            List<ComparisonSecuritySnapshot> snapshots
    ) implements ToolPayload {
        public ComparisonEvidence {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        }

        /** 返回只包含归约后实际新增证据的负载。 */
        public ComparisonEvidence withEvidence(List<FinancialEvidenceItem> added) {
            return new ComparisonEvidence(added, snapshots);
        }
    }

    /** @param metrics Java确定性计算的金融指标。 */
    record Metrics(List<FinancialMetricResult> metrics) implements ToolPayload {
        public Metrics {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
        }
    }

    /** @param riskAssessment 确定性风险评估。 */
    record Risk(FinancialRiskAssessment riskAssessment) implements ToolPayload {
    }

    /** @param research 多空条件研究。 @param policyVersion 规则版本。 */
    record BullBear(BullBearResearchResult research, String policyVersion) implements ToolPayload {
        public BullBear {
            policyVersion = policyVersion == null ? "" : policyVersion;
        }
    }

    /**
     * @param coverage 证据覆盖率。
     * @param effectiveEvidenceCount 有效证据数量。
     * @param missing 缺失项。
     * @param ready 是否可进入综合。
     */
    record Coverage(
            BigDecimal coverage,
            long effectiveEvidenceCount,
            List<String> missing,
            boolean ready
    ) implements ToolPayload {
        public Coverage {
            coverage = coverage == null ? BigDecimal.ZERO : coverage;
            missing = missing == null ? List.of() : List.copyOf(missing);
        }
    }
}
