package com.zzy.finsight.financial;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FinancialSnapshotRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FinancialSnapshotRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long saveSnapshot(long ownerId, long taskId, String threadId, FinancialSnapshot snapshot, String status) {
        return saveSnapshot(ownerId, taskId, threadId, snapshot, status, "");
    }

    public long saveSnapshot(
            long ownerId,
            long taskId,
            String threadId,
            FinancialSnapshot snapshot,
            String status,
            String dataSnapshotHash
    ) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stock_analysis_snapshot(
                      owner_id, task_id, thread_id, ticker, exchange_code, company_name, industry,
                      report_period, search_mode, snapshot_json, data_snapshot_hash, status, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            StockSubject subject = snapshot.subject();
            statement.setLong(1, ownerId);
            statement.setLong(2, taskId);
            statement.setString(3, threadId);
            statement.setString(4, subject.ticker());
            statement.setString(5, subject.exchange());
            statement.setString(6, subject.companyName());
            statement.setString(7, subject.industry());
            statement.setString(8, snapshot.reportPeriod());
            statement.setString(9, snapshot.searchMode());
            statement.setString(10, toJson(snapshot));
            statement.setString(11, dataSnapshotHash);
            statement.setString(12, status);
            statement.setObject(13, now);
            statement.setObject(14, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("保存股票分析快照后未返回主键");
        }
        long snapshotId = key.longValue();
        saveEvidence(snapshotId, taskId, snapshot.evidenceItems());
        return snapshotId;
    }

    public void saveMetrics(long snapshotId, long taskId, List<FinancialMetricResult> metrics) {
        if (hasMetrics(taskId)) {
            return;
        }
        for (FinancialMetricResult metric : metrics) {
            jdbcClient.sql("""
                            INSERT INTO stock_metric_result(
                              snapshot_id, task_id, metric_name, formula, formula_version, metric_value, display_value,
                              status, reason, evidence_refs, created_at
                            )
                            VALUES (:snapshotId, :taskId, :metricName, :formula, :formulaVersion, :metricValue, :displayValue,
                                    :status, :reason, :evidenceRefs, :createdAt)
                            """)
                    .param("snapshotId", snapshotId)
                    .param("taskId", taskId)
                    .param("metricName", metric.metricName())
                    .param("formula", metric.formula())
                    .param("formulaVersion", metric.formulaVersion())
                    .param("metricValue", metric.value())
                    .param("displayValue", metric.displayValue())
                    .param("status", metric.status())
                    .param("reason", metric.reason())
                    .param("evidenceRefs", toJson(metric.evidenceRefs()))
                    .param("createdAt", LocalDateTime.now())
                    .update();
        }
    }

    public Optional<PersistedFinancialSnapshot> findSnapshot(long ownerId, long taskId) {
        return jdbcTemplate.query("""
                        SELECT id, snapshot_json, data_snapshot_hash
                        FROM stock_analysis_snapshot
                        WHERE owner_id = ? AND task_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> new PersistedFinancialSnapshot(
                        rs.getLong("id"),
                        readSnapshot(rs.getString("snapshot_json")),
                        rs.getString("data_snapshot_hash")
                ),
                ownerId,
                taskId
        ).stream().findFirst();
    }

    public List<FinancialMetricResult> findMetrics(long taskId) {
        return jdbcTemplate.query("""
                        SELECT metric_name, formula, formula_version, metric_value, display_value,
                               status, reason, evidence_refs
                        FROM stock_metric_result
                        WHERE task_id = ?
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> new FinancialMetricResult(
                        rs.getString("metric_name"),
                        rs.getBigDecimal("metric_value"),
                        rs.getString("display_value"),
                        rs.getString("formula"),
                        rs.getString("formula_version"),
                        rs.getString("status"),
                        rs.getString("reason"),
                        readStringList(rs.getString("evidence_refs"))
                ),
                taskId
        );
    }

    public void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request) {
        String replaySnapshot = findReplay(ownerId, taskId)
                .map(StockReportReplayResponse::snapshotJson)
                .orElse("{}");
        jdbcClient.sql("""
                        INSERT INTO stock_bad_case_feedback(
                          owner_id, task_id, feedback_type, detail, replay_snapshot_json, created_at
                        )
                        VALUES (:ownerId, :taskId, :feedbackType, :detail, :replaySnapshotJson, :createdAt)
                        """)
                .param("ownerId", ownerId)
                .param("taskId", taskId)
                .param("feedbackType", request.getFeedbackType())
                .param("detail", request.getDetail())
                .param("replaySnapshotJson", replaySnapshot)
                .param("createdAt", LocalDateTime.now())
                .update();
    }

    public Optional<StockReportReplayResponse> findReplay(long ownerId, long taskId) {
        List<String> snapshots = jdbcTemplate.queryForList("""
                        SELECT snapshot_json
                        FROM stock_analysis_snapshot
                        WHERE owner_id = ? AND task_id = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                String.class,
                ownerId,
                taskId
        );
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        List<String> evidence = jdbcTemplate.queryForList("""
                        SELECT JSON_OBJECT(
                          'sourceType', source_type,
                          'sourceName', source_name,
                          'url', url,
                          'reportPeriod', report_period,
                          'metricName', metric_name,
                          'rawValue', raw_value,
                          'normalizedValue', normalized_value,
                          'excerpt', excerpt,
                          'confidence', confidence,
                          'issueCode', issue_code
                        )
                        FROM stock_evidence_item
                        WHERE task_id = ?
                        ORDER BY id ASC
                        """,
                String.class,
                taskId
        );
        List<String> metrics = jdbcTemplate.queryForList("""
                        SELECT JSON_OBJECT(
                          'metricName', metric_name,
                          'formula', formula,
                          'formulaVersion', formula_version,
                          'value', metric_value,
                          'displayValue', display_value,
                          'status', status,
                          'reason', reason,
                          'evidenceRefs', evidence_refs
                        )
                        FROM stock_metric_result
                        WHERE task_id = ?
                        ORDER BY id ASC
                        """,
                String.class,
                taskId
        );
        return Optional.of(new StockReportReplayResponse(taskId, snapshots.get(0), evidence, metrics));
    }

    private void saveEvidence(long snapshotId, long taskId, List<FinancialEvidenceItem> evidenceItems) {
        for (FinancialEvidenceItem item : evidenceItems) {
            jdbcClient.sql("""
                            INSERT INTO stock_evidence_item(
                              snapshot_id, task_id, source_type, source_name, url, page_number, report_period,
                              metric_name, raw_value, normalized_value, excerpt, confidence, as_of_time,
                              issue_code, created_at
                            )
                            VALUES (:snapshotId, :taskId, :sourceType, :sourceName, :url, :pageNumber, :reportPeriod,
                                    :metricName, :rawValue, :normalizedValue, :excerpt, :confidence, :asOfTime,
                                    :issueCode, :createdAt)
                            """)
                    .param("snapshotId", snapshotId)
                    .param("taskId", taskId)
                    .param("sourceType", item.sourceType())
                    .param("sourceName", item.sourceName())
                    .param("url", item.url())
                    .param("pageNumber", item.pageNumber())
                    .param("reportPeriod", item.reportPeriod())
                    .param("metricName", item.metricName())
                    .param("rawValue", item.rawValue())
                    .param("normalizedValue", item.normalizedValue())
                    .param("excerpt", item.excerpt())
                    .param("confidence", item.confidence())
                    .param("asOfTime", item.asOf())
                    .param("issueCode", item.issueCode())
                    .param("createdAt", LocalDateTime.now())
                    .update();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private boolean hasMetrics(long taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_metric_result WHERE task_id = ?",
                Integer.class,
                taskId
        );
        return count != null && count > 0;
    }

    private FinancialSnapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, FinancialSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("读取金融快照JSON失败", e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
