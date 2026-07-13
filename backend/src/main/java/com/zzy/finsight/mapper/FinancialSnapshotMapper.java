package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.PersistedFinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 定义金融快照、证据、指标和反馈的数据访问操作。
 */
@Mapper
public interface FinancialSnapshotMapper {
    default long saveSnapshot(long ownerId, long taskId, String threadId, FinancialSnapshot snapshot, String status) {
        return saveSnapshot(ownerId, taskId, threadId, snapshot, status, "");
    }

    default long saveSnapshot(
            long ownerId,
            long taskId,
            String threadId,
            FinancialSnapshot snapshot,
            String status,
            String dataSnapshotHash
    ) {
        StockSubject subject = snapshot.subject();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("ownerId", ownerId);
        command.put("taskId", taskId);
        command.put("threadId", threadId);
        command.put("ticker", subject.ticker());
        command.put("exchangeCode", subject.exchange());
        command.put("companyName", subject.companyName());
        command.put("industry", subject.industry());
        command.put("reportPeriod", snapshot.reportPeriod());
        command.put("searchMode", snapshot.searchMode());
        command.put("snapshot", snapshot);
        command.put("dataSnapshotHash", dataSnapshotHash);
        command.put("status", status);
        command.put("createdAt", now);
        command.put("updatedAt", now);
        insertSnapshot(command);
        Number id = (Number) command.get("id");
        if (id == null) {
            throw new IllegalStateException("保存股票分析快照后未返回主键");
        }
        long snapshotId = id.longValue();
        for (FinancialEvidenceItem item : snapshot.evidenceItems()) {
            insertEvidence(evidenceCommand(snapshotId, taskId, item));
        }
        return snapshotId;
    }

    int insertSnapshot(Map<String, Object> command);

    int insertEvidence(Map<String, Object> command);

    default void saveMetrics(long snapshotId, long taskId, List<FinancialMetricResult> metrics) {
        if (countMetrics(taskId) > 0) {
            return;
        }
        for (FinancialMetricResult metric : metrics) {
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("snapshotId", snapshotId);
            command.put("taskId", taskId);
            command.put("metricName", metric.metricName());
            command.put("formula", metric.formula());
            command.put("formulaVersion", metric.formulaVersion());
            command.put("metricValue", metric.value());
            command.put("displayValue", metric.displayValue());
            command.put("status", metric.status());
            command.put("reason", metric.reason());
            command.put("evidenceRefs", metric.evidenceRefs());
            command.put("createdAt", LocalDateTime.now());
            insertMetric(command);
        }
    }

    int countMetrics(@Param("taskId") long taskId);

    int insertMetric(Map<String, Object> command);

    Optional<PersistedFinancialSnapshot> findSnapshot(
            @Param("ownerId") long ownerId,
            @Param("taskId") long taskId
    );

    List<FinancialMetricResult> findMetrics(@Param("taskId") long taskId);

    default void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request) {
        String replaySnapshot = findReplay(ownerId, taskId)
                .map(StockReportReplayResponse::snapshotJson)
                .orElse("{}");
        insertFeedback(ownerId, taskId, request.getFeedbackType(), request.getDetail(), replaySnapshot, LocalDateTime.now());
    }

    int insertFeedback(
            @Param("ownerId") long ownerId,
            @Param("taskId") long taskId,
            @Param("feedbackType") String feedbackType,
            @Param("detail") String detail,
            @Param("replaySnapshotJson") String replaySnapshotJson,
            @Param("createdAt") LocalDateTime createdAt
    );

    default Optional<StockReportReplayResponse> findReplay(long ownerId, long taskId) {
        Optional<String> snapshot = findLatestSnapshotJson(ownerId, taskId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StockReportReplayResponse(
                taskId,
                snapshot.orElseThrow(),
                findEvidenceJson(taskId),
                findMetricJson(taskId)
        ));
    }

    Optional<String> findLatestSnapshotJson(@Param("ownerId") long ownerId, @Param("taskId") long taskId);

    List<String> findEvidenceJson(@Param("taskId") long taskId);

    List<String> findMetricJson(@Param("taskId") long taskId);

    private static Map<String, Object> evidenceCommand(long snapshotId, long taskId, FinancialEvidenceItem item) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("snapshotId", snapshotId);
        command.put("taskId", taskId);
        command.put("sourceType", item.sourceType());
        command.put("sourceName", item.sourceName());
        command.put("url", item.url());
        command.put("pageNumber", item.pageNumber());
        command.put("reportPeriod", item.reportPeriod());
        command.put("metricName", item.metricName());
        command.put("rawValue", item.rawValue());
        command.put("normalizedValue", item.normalizedValue());
        command.put("excerpt", item.excerpt());
        command.put("confidence", item.confidence());
        command.put("asOfTime", item.asOf());
        command.put("issueCode", item.issueCode());
        command.put("createdAt", LocalDateTime.now());
        return command;
    }
}
