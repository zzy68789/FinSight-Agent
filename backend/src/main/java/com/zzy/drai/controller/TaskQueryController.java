package com.zzy.drai.controller;

import com.zzy.drai.dto.AgentStepLogResponse;
import com.zzy.drai.dto.ApiResponse;
import com.zzy.drai.dto.PageResponse;
import com.zzy.drai.dto.ReportResponse;
import com.zzy.drai.dto.TaskDetailResponse;
import com.zzy.drai.dto.TaskSummaryResponse;
import com.zzy.drai.service.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TaskQueryController {
    private final TaskQueryService taskQueryService;

    public TaskQueryController(TaskQueryService taskQueryService) {
        this.taskQueryService = taskQueryService;
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<TaskSummaryResponse>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(taskQueryService.listTasks(page, size, status, keyword));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable long taskId) {
        return ApiResponse.success(taskQueryService.getTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/logs")
    public ApiResponse<List<AgentStepLogResponse>> getTaskLogs(@PathVariable long taskId) {
        return ApiResponse.success(taskQueryService.getTaskLogs(taskId));
    }

    @GetMapping("/threads/{threadId}/reports")
    public ApiResponse<List<ReportResponse>> getThreadReports(@PathVariable String threadId) {
        return ApiResponse.success(taskQueryService.getThreadReports(threadId));
    }

    @GetMapping("/reports/{reportId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable long reportId) {
        return ApiResponse.success(taskQueryService.getReport(reportId));
    }
}
