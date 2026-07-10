package com.zzy.drai.financial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.drai.domain.AgentStepLogRecord;
import com.zzy.drai.domain.ReportRecord;
import com.zzy.drai.domain.WorkflowTaskExecutionRecord;
import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.ReportRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StockReportTraceService {
    private final ResearchTaskRepository taskRepository;
    private final AgentStepLogRepository stepLogRepository;
    private final FinancialSnapshotRepository snapshotRepository;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    public StockReportTraceService(
            ResearchTaskRepository taskRepository,
            AgentStepLogRepository stepLogRepository,
            FinancialSnapshotRepository snapshotRepository,
            ReportRepository reportRepository,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.stepLogRepository = stepLogRepository;
        this.snapshotRepository = snapshotRepository;
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
    }

    public StockReportTraceResponse get(long ownerId, long taskId) {
        WorkflowTaskExecutionRecord task = taskRepository.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告任务"));
        Optional<PersistedFinancialSnapshot> persisted = snapshotRepository.findSnapshot(ownerId, taskId);
        Optional<ReportRecord> report = reportRepository.findReportByTask(ownerId, taskId);
        List<AgentStepLogRecord> logs = stepLogRepository.findByTaskId(taskId);

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
