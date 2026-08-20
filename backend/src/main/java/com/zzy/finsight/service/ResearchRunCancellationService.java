package com.zzy.finsight.service;

/**
 * 定义 Research Agent 任务取消业务。
 */
public interface ResearchRunCancellationService {
    /** 取消当前用户拥有且尚未结束的研究任务。 */
    void cancel(long ownerId, long taskId);
}
