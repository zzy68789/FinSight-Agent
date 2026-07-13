package com.zzy.finsight.service;

import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.TaskDetailResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;

import java.util.List;

/**
 * 定义任务和报告查询管理业务。
 */
public interface TaskQueryService {
    /** 分页查询当前用户的任务。 */
    PageResponse<TaskSummaryResponse> listTasks(long ownerId, int page, int size, String status, String keyword);

    /** 查询当前用户的任务详情。 */
    TaskDetailResponse getTask(long ownerId, long taskId);

    /** 查询当前用户任务的步骤日志。 */
    List<AgentStepLogResponse> getTaskLogs(long ownerId, long taskId);

    /** 查询线程下的全部报告版本。 */
    List<ReportResponse> getThreadReports(long ownerId, String threadId);

    /** 按条件查询当前用户的报告。 */
    List<ReportResponse> listReports(long ownerId, String keyword, boolean favoriteOnly);

    /** 查询当前用户的指定报告。 */
    ReportResponse getReport(long ownerId, long reportId);

    /** 更新报告收藏状态。 */
    ReportResponse updateFavorite(long ownerId, long reportId, boolean favorite);

    /** 软删除当前用户的报告。 */
    void deleteReport(long ownerId, long reportId);

    /** 将报告内容追加到当前知识库。 */
    ReportIndexResponse indexReportToKnowledgeBase(long ownerId, long reportId);
}
