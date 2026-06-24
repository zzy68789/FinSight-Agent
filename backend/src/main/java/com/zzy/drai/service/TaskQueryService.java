package com.zzy.drai.service;

import com.zzy.drai.domain.AgentStepLogRecord;
import com.zzy.drai.domain.ReportRecord;
import com.zzy.drai.domain.ResearchTaskRecord;
import com.zzy.drai.dto.AgentStepLogResponse;
import com.zzy.drai.dto.PageResponse;
import com.zzy.drai.dto.ReportResponse;
import com.zzy.drai.dto.TaskDetailResponse;
import com.zzy.drai.dto.TaskSummaryResponse;
import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.ReportRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskQueryService {
    private final ResearchTaskRepository taskRepository;
    private final AgentStepLogRepository stepLogRepository;
    private final ReportRepository reportRepository;

    public TaskQueryService(
            ResearchTaskRepository taskRepository,
            AgentStepLogRepository stepLogRepository,
            ReportRepository reportRepository
    ) {
        this.taskRepository = taskRepository;
        this.stepLogRepository = stepLogRepository;
        this.reportRepository = reportRepository;
    }

    public PageResponse<TaskSummaryResponse> listTasks(int page, int size, String status, String keyword) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        List<TaskSummaryResponse> items = taskRepository.findPage(normalizedPage, normalizedSize, normalize(status), normalize(keyword))
                .stream()
                .map(this::toSummary)
                .toList();
        long total = taskRepository.count(normalize(status), normalize(keyword));
        return new PageResponse<>(items, normalizedPage, normalizedSize, total);
    }

    public TaskDetailResponse getTask(long taskId) {
        return taskRepository.findById(taskId)
                .map(this::toDetail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    public List<AgentStepLogResponse> getTaskLogs(long taskId) {
        getTask(taskId);
        return stepLogRepository.findByTaskId(taskId).stream()
                .map(this::toStepLog)
                .toList();
    }

    public List<ReportResponse> getThreadReports(String threadId) {
        return reportRepository.findReportsByThread(threadId).stream()
                .map(this::toReport)
                .toList();
    }

    public ReportResponse getReport(long reportId) {
        return reportRepository.findReportById(reportId)
                .map(this::toReport)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "报告不存在"));
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
                report.createdAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
