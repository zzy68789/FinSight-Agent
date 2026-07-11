package com.zzy.finsight.financial;

public record PersistedFinancialSnapshot(
        long id,
        FinancialSnapshot snapshot,
        String dataSnapshotHash
) {
}
