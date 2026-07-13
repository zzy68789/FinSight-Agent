package com.zzy.finsight.domain.stock;


public record PersistedFinancialSnapshot(
        long id,
        FinancialSnapshot snapshot,
        String dataSnapshotHash
) {
}
