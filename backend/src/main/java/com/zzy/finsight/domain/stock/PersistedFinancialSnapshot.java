package com.zzy.finsight.domain.stock;


/**
 * 表示已持久化的金融快照及其指纹。
 * @param id 主键标识。
 * @param snapshot 金融数据快照。
 * @param dataSnapshotHash 数据快照摘要。
 */
public record PersistedFinancialSnapshot(
        long id,
        FinancialSnapshot snapshot,
        String dataSnapshotHash
) {
}
