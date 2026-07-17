package com.zzy.finsight.component.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.WorkflowCheckpointRecord;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 将 Writer 和 Reviewer 检查点解析为可恢复的强类型状态。
 */
@Component
public class WorkflowCheckpointCodec {
    private static final int MAX_WRITER_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;

    public WorkflowCheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析 Writer 检查点，非法或不完整状态返回空。 */
    public Optional<WriterCheckpointState> writer(WorkflowCheckpointRecord checkpoint) {
        if (!validCheckpoint(checkpoint, "WRITER")) {
            return Optional.empty();
        }
        try {
            JsonNode state = objectMapper.readTree(checkpoint.stateJson());
            if (!attemptMatches(checkpoint, state)) {
                return Optional.empty();
            }
            String report = state.path("finalReport").asText("");
            return report.isBlank()
                    ? Optional.empty()
                    : Optional.of(new WriterCheckpointState(checkpoint.attemptNo(), report));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** 解析 Reviewer 检查点，非法或不完整状态返回空。 */
    public Optional<ReviewerCheckpointState> reviewer(WorkflowCheckpointRecord checkpoint) {
        if (!validCheckpoint(checkpoint, "REVIEWER")) {
            return Optional.empty();
        }
        try {
            JsonNode state = objectMapper.readTree(checkpoint.stateJson());
            if (!attemptMatches(checkpoint, state)) {
                return Optional.empty();
            }
            String reviewStatus = state.path("reviewStatus").asText("");
            String critique = state.path("critique").asText("");
            JsonNode complianceNode = state.path("compliance");
            if (!validStatus(reviewStatus) || complianceNode.isMissingNode() || complianceNode.isNull()) {
                return Optional.empty();
            }
            FinancialComplianceReviewResult compliance = objectMapper.treeToValue(
                    complianceNode,
                    FinancialComplianceReviewResult.class
            );
            if (compliance == null || !validStatus(compliance.status())) {
                return Optional.empty();
            }
            return Optional.of(new ReviewerCheckpointState(
                    checkpoint.attemptNo(),
                    new CitationReviewResult(reviewStatus, critique),
                    compliance
            ));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean validCheckpoint(WorkflowCheckpointRecord checkpoint, String expectedStage) {
        return checkpoint != null
                && expectedStage.equals(checkpoint.stage())
                && checkpoint.attemptNo() >= 1
                && checkpoint.attemptNo() <= MAX_WRITER_ATTEMPTS
                && checkpoint.generationContextHash() != null
                && !checkpoint.generationContextHash().isBlank()
                && checkpoint.stateJson() != null
                && !checkpoint.stateJson().isBlank();
    }

    private boolean attemptMatches(WorkflowCheckpointRecord checkpoint, JsonNode state) {
        return state != null
                && state.isObject()
                && state.path("attempt").canConvertToInt()
                && state.path("attempt").asInt() == checkpoint.attemptNo();
    }

    private boolean validStatus(String status) {
        return "PASS".equals(status) || "FAIL".equals(status);
    }
}

/**
 * @param attempt Writer 尝试次数
 * @param report 已生成报告正文
 */
record WriterCheckpointState(int attempt, String report) {
}

/**
 * @param attempt Reviewer 对应的 Writer 尝试次数
 * @param review 引用审查结果
 * @param compliance 合规审查结果
 */
record ReviewerCheckpointState(
        int attempt,
        CitationReviewResult review,
        FinancialComplianceReviewResult compliance
) {
}
