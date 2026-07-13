package com.zzy.finsight.service.impl;

import com.zzy.finsight.domain.AgentStepLogRecord;
import com.zzy.finsight.domain.ReportRecord;
import com.zzy.finsight.domain.ResearchTaskRecord;
import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.TaskDetailResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;
import com.zzy.finsight.rag.RagService;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskQueryServiceImpl implements TaskQueryService {
    private final ResearchTaskMapper taskMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final ReportMapper reportMapper;
    private final RagService ragService;

    public TaskQueryServiceImpl(
            ResearchTaskMapper taskMapper,
            AgentStepLogMapper stepLogMapper,
            ReportMapper reportMapper,
            RagService ragService
    ) {
        this.taskMapper = taskMapper;
        this.stepLogMapper = stepLogMapper;
        this.reportMapper = reportMapper;
        this.ragService = ragService;
    }

    public PageResponse<TaskSummaryResponse> listTasks(long ownerId, int page, int size, String status, String keyword) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        List<TaskSummaryResponse> items = taskMapper.findPage(ownerId, normalizedPage, normalizedSize, normalize(status), normalize(keyword))
                .stream()
                .map(this::toSummary)
                .toList();
        long total = taskMapper.count(ownerId, normalize(status), normalize(keyword));
        return new PageResponse<>(items, normalizedPage, normalizedSize, total);
    }

    public TaskDetailResponse getTask(long ownerId, long taskId) {
        return taskMapper.findById(ownerId, taskId)
                .map(this::toDetail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public List<AgentStepLogResponse> getTaskLogs(long ownerId, long taskId) {
        getTask(ownerId, taskId);
        return stepLogMapper.findByTaskId(taskId).stream()
                .map(this::toStepLog)
                .toList();
    }

    public List<ReportResponse> getThreadReports(long ownerId, String threadId) {
        return reportMapper.findReportsByThread(ownerId, threadId).stream()
                .map(this::toReport)
                .toList();
    }

    public List<ReportResponse> listReports(long ownerId, String keyword, boolean favoriteOnly) {
        return reportMapper.findReports(ownerId, normalize(keyword), favoriteOnly).stream()
                .map(this::toReport)
                .toList();
    }

    public ReportResponse getReport(long ownerId, long reportId) {
        return reportMapper.findReportById(ownerId, reportId)
                .map(this::toReport)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    public ReportResponse updateFavorite(long ownerId, long reportId, boolean favorite) {
        getReport(ownerId, reportId);
        reportMapper.updateFavorite(ownerId, reportId, favorite);
        return getReport(ownerId, reportId);
    }

    public void deleteReport(long ownerId, long reportId) {
        getReport(ownerId, reportId);
        reportMapper.softDelete(ownerId, reportId);
    }

    public ReportIndexResponse indexReportToKnowledgeBase(long ownerId, long reportId) {
        ReportRecord report = reportMapper.findReportById(ownerId, reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        int chunksStored = ragService.indexText(reportSource(report), report.content());
        reportMapper.markIndexed(ownerId, reportId);
        return new ReportIndexResponse(reportId, chunksStored, "indexed");
    }

    private TaskSummaryResponse toSummary(ResearchTaskRecord task) {
        return new TaskSummaryResponse(
                task.id(),
                task.threadId(),
                task.query(),
                task.searchMode(),
                task.status(),
                task.revisionNumber(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private TaskDetailResponse toDetail(ResearchTaskRecord task) {
        return new TaskDetailResponse(
                task.id(),
                task.threadId(),
                task.query(),
                task.searchMode(),
                task.status(),
                task.revisionNumber(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private AgentStepLogResponse toStepLog(AgentStepLogRecord log) {
        return new AgentStepLogResponse(
                log.id(),
                log.taskId(),
                log.stepName(),
                log.inputSnapshot(),
                log.outputSnapshot(),
                log.status(),
                log.errorMessage(),
                log.attemptNo(),
                log.durationMs(),
                log.createdAt()
        );
    }

    private ReportResponse toReport(ReportRecord report) {
        return new ReportResponse(
                report.id(),
                report.taskId(),
                report.threadId(),
                report.content(),
                report.version(),
                report.reviewStatus(),
                report.critique(),
                report.createdAt(),
                report.favorite(),
                report.indexedAt()
        );
    }

    private String reportSource(ReportRecord report) {
        return "report-%s-v%d.md".formatted(report.threadId(), report.version());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
