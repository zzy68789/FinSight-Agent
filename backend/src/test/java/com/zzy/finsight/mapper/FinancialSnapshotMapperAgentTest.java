package com.zzy.finsight.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zzy.finsight.domain.stock.FinancialEvidenceArbitration;
import com.zzy.finsight.domain.stock.FinancialEvidenceArbitrationCandidate;
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

    @Test
    void synchronizesReArbitratedIssueCodeByStableEvidenceKey() {
        FinancialSnapshotMapper mapper = mock(FinancialSnapshotMapper.class, CALLS_REAL_METHODS);
        FinancialEvidenceItem evidence = new FinancialEvidenceItem(
                "UPLOADED_REPORT",
                "上传报告",
                "",
                null,
                "20260630",
                "NET_PROFIT",
                new BigDecimal("100"),
                new BigDecimal("100"),
                "净利润为 100 亿元",
                new BigDecimal("0.80"),
                LocalDateTime.of(2026, 8, 12, 10, 0),
                "SOURCE_CONFLICT_REJECTED"
        );

        mapper.synchronizeEvidenceIssues(51L, 11L, List.of(evidence));

        ArgumentCaptor<String> evidenceKey = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateEvidenceIssue(
                org.mockito.ArgumentMatchers.eq(51L),
                org.mockito.ArgumentMatchers.eq(11L),
                evidenceKey.capture(),
                org.mockito.ArgumentMatchers.eq("SOURCE_CONFLICT_REJECTED")
        );
        assertThat(evidenceKey.getValue()).hasSize(64);
    }

    @Test
    void snapshotJsonKeepsArbitrationAndReadsLegacyPayloadWithoutField() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        FinancialEvidenceArbitration arbitration = new FinancialEvidenceArbitration(
                "NET_PROFIT",
                "20260630",
                FinancialEvidenceArbitration.Status.RESOLVED,
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                new BigDecimal("120"),
                List.of(new FinancialEvidenceArbitrationCandidate(
                        "AUTHORIZED_MARKET",
                        "TuShare Pro",
                        new BigDecimal("120"),
                        100,
                        new BigDecimal("0.90"),
                        FinancialEvidenceArbitrationCandidate.Decision.SELECTED
                )),
                "授权来源优先",
                "test-policy-v1"
        );
        FinancialSnapshot snapshot = new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260630",
                "hybrid",
                List.of(),
                List.of(arbitration),
                List.of(),
                List.of(),
                List.of(),
                null,
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );

        String json = objectMapper.writeValueAsString(snapshot);
        FinancialSnapshot restored = objectMapper.readValue(json, FinancialSnapshot.class);
        ObjectNode legacyJson = (ObjectNode) objectMapper.readTree(json);
        legacyJson.remove("evidenceArbitrations");
        FinancialSnapshot restoredLegacy = objectMapper.treeToValue(legacyJson, FinancialSnapshot.class);

        assertThat(restored.evidenceArbitrations()).containsExactly(arbitration);
        assertThat(restoredLegacy.evidenceArbitrations()).isEmpty();
    }
}
