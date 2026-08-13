package com.zzy.finsight.domain.stock;

/**
 * 表示一次可比证券采集产生的独立只读快照索引。
 *
 * @param snapshotId 基于证券、截止日和证据生成的稳定快照标识。
 * @param subject 已解析的可比证券主体。
 * @param reportPeriod 数据截止日。
 * @param evidenceCount 归属于该证券的证据数量。
 * @param effectiveEvidenceCount 有效证据数量。
 * @param status 快照状态，数据不足时为 DATA_MISSING。
 */
public record ComparisonSecuritySnapshot(
        String snapshotId,
        StockSubject subject,
        String reportPeriod,
        int evidenceCount,
        long effectiveEvidenceCount,
        String status
) {
    public ComparisonSecuritySnapshot {
        snapshotId = snapshotId == null ? "" : snapshotId;
        reportPeriod = reportPeriod == null ? "" : reportPeriod;
        evidenceCount = Math.max(0, evidenceCount);
        effectiveEvidenceCount = Math.max(0L, effectiveEvidenceCount);
        status = status == null || status.isBlank() ? "DATA_MISSING" : status;
    }
}
