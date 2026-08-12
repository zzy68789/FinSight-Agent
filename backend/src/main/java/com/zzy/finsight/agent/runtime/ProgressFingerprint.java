package com.zzy.finsight.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根据计划问题、证据哈希、指标哈希和门禁路由判断已提交 turn 是否产生实质进展。
 */
@Component
public class ProgressFingerprint {
    private final ObjectMapper objectMapper;
    private final FinancialReportFingerprinter reportFingerprinter;

    public ProgressFingerprint(ObjectMapper objectMapper, FinancialReportFingerprinter reportFingerprinter) {
        this.objectMapper = objectMapper;
        this.reportFingerprinter = reportFingerprinter;
    }

    /** 生成与计数器、阶段和自由文案无关的稳定进展摘要。 */
    public String capture(AgentState state) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("unresolvedQuestions", state.getPlan() == null
                ? java.util.List.of() : state.getPlan().unresolvedQuestions());
        canonical.put("requiredEvidence", state.getPlan() == null
                ? java.util.List.of() : state.getPlan().requiredEvidence());
        canonical.put("evidenceHash", state.getSnapshot() == null
                ? "" : reportFingerprinter.dataSnapshotHash(state.getSnapshot()));
        canonical.put("metrics", state.getMetrics());
        canonical.put("qualityRoute", state.getQualityGateDecision() == null
                ? "" : state.getQualityGateDecision().route().name());
        canonical.put("recoveryStatus", state.getEvidenceRecoveryDirective() == null
                ? "" : state.getEvidenceRecoveryDirective().status());
        try {
            byte[] bytes = objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成 Agent 进展指纹", exception);
        }
    }

    /** 更新连续无进展计数，并返回当前计数。 */
    public int update(AgentState state) {
        String current = capture(state);
        if (state.getLastProgressFingerprint().isBlank()) {
            state.setConsecutiveNoProgressTurns(0);
        } else if (state.getLastProgressFingerprint().equals(current)) {
            state.setConsecutiveNoProgressTurns(state.getConsecutiveNoProgressTurns() + 1);
        } else {
            state.setConsecutiveNoProgressTurns(0);
        }
        state.setLastProgressFingerprint(current);
        return state.getConsecutiveNoProgressTurns();
    }
}
