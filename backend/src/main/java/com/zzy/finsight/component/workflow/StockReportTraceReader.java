package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.PersistedFinancialSnapshot;
import com.zzy.finsight.dto.stock.StockReportStageTrace;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.AgentStepLogRecord;
import com.zzy.finsight.domain.ReportRecord;
import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 聚合任务、快照、报告和步骤日志形成可信度轨迹。
 */
@Component
public class StockReportTraceReader {
    private final ResearchTaskMapper taskMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final FinancialSnapshotMapper snapshotMapper;
    private final ReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    public StockReportTraceReader(
            ResearchTaskMapper taskMapper,
            AgentStepLogMapper stepLogMapper,
            FinancialSnapshotMapper snapshotMapper,
            ReportMapper reportMapper,
            ObjectMapper objectMapper
    ) {
        this.taskMapper = taskMapper;
        this.stepLogMapper = stepLogMapper;
        this.snapshotMapper = snapshotMapper;
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
    }

    /** 查询当前用户可访问的投研任务追踪详情。 */
    public StockReportTraceResponse get(long ownerId, long taskId) {
        WorkflowTaskExecutionRecord task = taskMapper.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告任务"));
        Optional<PersistedFinancialSnapshot> persisted = snapshotMapper.findSnapshot(ownerId, taskId);
        Optional<ReportRecord> report = reportMapper.findReportByTask(ownerId, taskId);
        List<AgentStepLogRecord> logs = stepLogMapper.findByTaskId(taskId);

        FinancialSnapshot snapshot = persisted.map(PersistedFinancialSnapshot::snapshot).orElse(null);
        int evidenceTotal = snapshot == null ? 0 : snapshot.evidenceItems().size();
        int evidenceEffective = snapshot == null ? 0 : (int) snapshot.evidenceItems().stream()
                .filter(FinancialEvidenceItem::effective)
                .count();
        List<StockReportStageTrace> stages = logs.stream()
                .map(log -> new StockReportStageTrace(
                        log.stepName(), log.attemptNo(), log.status(), log.durationMs(), log.errorMessage(), log.createdAt()
                ))
                .toList();

        Map<String, JsonNode> reviewSummary = new LinkedHashMap<>();
        for (AgentStepLogRecord log : logs) {
            if (List.of("reviewer", "evaluation", "cache_hit", "done").contains(log.stepName())
                    && log.outputSnapshot() != null) {
                reviewSummary.put(log.stepName(), readJson(log.outputSnapshot()));
            }
        }
        String dataHash = report.map(ReportRecord::dataSnapshotHash)
                .filter(value -> value != null && !value.isBlank())
                .orElseGet(() -> persisted.map(PersistedFinancialSnapshot::dataSnapshotHash).orElse(null));
        return new StockReportTraceResponse(
                taskId,
                task.status(),
                task.stage(),
                task.attemptCount(),
                report.map(ReportRecord::version).orElse(null),
                dataHash,
                report.map(ReportRecord::generationContextHash).orElse(null),
                report.map(ReportRecord::reusedFromReportId).isPresent(),
                report.map(ReportRecord::reusedFromReportId).orElse(null),
                evidenceTotal,
                evidenceEffective,
                Math.max(0, evidenceTotal - evidenceEffective),
                snapshot == null ? List.of() : snapshot.retrievalResults(),
                stages,
                reviewSummary
        );
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("parseError", true);
        }
    }
}
