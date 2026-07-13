package com.zzy.finsight.service;

import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.TaskDetailResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;

import java.util.List;

public interface TaskQueryService {
    PageResponse<TaskSummaryResponse> listTasks(long ownerId, int page, int size, String status, String keyword);

    TaskDetailResponse getTask(long ownerId, long taskId);

    List<AgentStepLogResponse> getTaskLogs(long ownerId, long taskId);

    List<ReportResponse> getThreadReports(long ownerId, String threadId);

    List<ReportResponse> listReports(long ownerId, String keyword, boolean favoriteOnly);

    ReportResponse getReport(long ownerId, long reportId);

    ReportResponse updateFavorite(long ownerId, long reportId, boolean favorite);

    void deleteReport(long ownerId, long reportId);

    ReportIndexResponse indexReportToKnowledgeBase(long ownerId, long reportId);
}
