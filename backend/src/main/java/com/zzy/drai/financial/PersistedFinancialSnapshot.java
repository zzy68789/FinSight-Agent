package com.zzy.drai.financial;

public record PersistedFinancialSnapshot(
        long id,
        FinancialSnapshot snapshot,
        String dataSnapshotHash
) {
}
