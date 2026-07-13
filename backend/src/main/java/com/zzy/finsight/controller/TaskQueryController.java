package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.TaskDetailResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;
import com.zzy.finsight.dto.report.ExportedReport;
import com.zzy.finsight.service.ReportExportService;
import com.zzy.finsight.service.TaskQueryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供任务、报告、导出和知识库管理接口。
 */
@RestController
@RequestMapping("/api")
public class TaskQueryController {
    private final TaskQueryService taskQueryService;
    private final ReportExportService reportExportService;
    private final UserContext userContext;

    public TaskQueryController(TaskQueryService taskQueryService, ReportExportService reportExportService, UserContext userContext) {
        this.taskQueryService = taskQueryService;
        this.reportExportService = reportExportService;
        this.userContext = userContext;
    }

    /** 分页查询当前用户的任务。 */
    @GetMapping("/tasks")
    public ApiResponse<PageResponse<TaskSummaryResponse>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(taskQueryService.listTasks(userContext.currentUserId(), page, size, status, keyword));
    }

    /** 查询当前用户的任务详情。 */
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable long taskId) {
        return ApiResponse.success(taskQueryService.getTask(userContext.currentUserId(), taskId));
    }

    /** 查询当前用户任务的步骤日志。 */
    @GetMapping("/tasks/{taskId}/logs")
    public ApiResponse<List<AgentStepLogResponse>> getTaskLogs(@PathVariable long taskId) {
        return ApiResponse.success(taskQueryService.getTaskLogs(userContext.currentUserId(), taskId));
    }

    /** 查询指定会话下的报告版本。 */
    @GetMapping("/threads/{threadId}/reports")
    public ApiResponse<List<ReportResponse>> getThreadReports(@PathVariable String threadId) {
        return ApiResponse.success(taskQueryService.getThreadReports(userContext.currentUserId(), threadId));
    }

    /** 查询当前用户的报告列表。 */
    @GetMapping("/reports")
    public ApiResponse<List<ReportResponse>> listReports(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean favoriteOnly
    ) {
        return ApiResponse.success(taskQueryService.listReports(userContext.currentUserId(), keyword, favoriteOnly));
    }

    /** 查询当前用户的报告详情。 */
    @GetMapping("/reports/{reportId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable long reportId) {
        return ApiResponse.success(taskQueryService.getReport(userContext.currentUserId(), reportId));
    }

    /** 按指定格式导出报告文件。 */
    @GetMapping("/reports/{reportId}/export")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable long reportId,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        ReportResponse report = taskQueryService.getReport(userContext.currentUserId(), reportId);
        ExportedReport exported = reportExportService.export(report, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exported.filename())
                        .build()
                        .toString())
                .body(exported.bytes());
    }

    /** 更新报告收藏状态。 */
    @PostMapping("/reports/{reportId}/favorite")
    public ApiResponse<ReportResponse> updateFavorite(
            @PathVariable long reportId,
            @RequestParam(defaultValue = "true") boolean favorite
    ) {
        return ApiResponse.success(taskQueryService.updateFavorite(userContext.currentUserId(), reportId, favorite));
    }

    /** 软删除当前用户的报告。 */
    @DeleteMapping("/reports/{reportId}")
    public ApiResponse<Void> deleteReport(@PathVariable long reportId) {
        taskQueryService.deleteReport(userContext.currentUserId(), reportId);
        return ApiResponse.success(null);
    }

    /** 将报告内容追加到当前 RAG 知识库。 */
    @PostMapping("/reports/{reportId}/knowledge-base")
    public ApiResponse<ReportIndexResponse> indexReportToKnowledgeBase(@PathVariable long reportId) {
        return ApiResponse.success(taskQueryService.indexReportToKnowledgeBase(userContext.currentUserId(), reportId));
    }
}
