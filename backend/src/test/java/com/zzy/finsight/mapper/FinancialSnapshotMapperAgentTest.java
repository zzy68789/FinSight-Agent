package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinancialSnapshotMapperAgentTest {

    @Test
    void bindsInitialSnapshotEvidenceToCreatingToolCall() {
        FinancialSnapshotMapper mapper = mock(FinancialSnapshotMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            invocation.<Map<String, Object>>getArgument(0).put("id", 51L);
            return 1;
        }).when(mapper).insertSnapshot(anyMap());
        doAnswer(invocation -> 1).when(mapper).insertEvidence(anyMap());
        FinancialEvidenceItem evidence = new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "测试数据源",
                "https://example.com/report",
                null,
                "20260630",
                "OPERATING_REVENUE",
                new BigDecimal("100"),
                new BigDecimal("100"),
                "营业收入为 100 亿元",
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 8, 12, 10, 0),
                ""
        );
        FinancialSnapshot snapshot = new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260630",
                "hybrid",
                List.of(evidence),
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );

        long snapshotId = mapper.saveAgentSnapshot(
                7L, 11L, "agent-thread", snapshot, "COLLECTING", "data-hash", 41L
        );

        ArgumentCaptor<Map<String, Object>> command = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertEvidence(command.capture());
        assertThat(snapshotId).isEqualTo(51L);
        assertThat(command.getValue())
                .containsEntry("snapshotId", 51L)
                .containsEntry("taskId", 11L)
                .containsEntry("toolCallId", 41L);
        assertThat(command.getValue().get("evidenceKey")).asString().hasSize(64);
    }
}
