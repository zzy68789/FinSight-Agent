package com.zzy.finsight.service;

import com.zzy.finsight.domain.ReusableReportRecord;

import java.util.Optional;

/**
 * 定义报告保存、查询和复用业务。
 */
public interface ReportService {
    /** 查询线程下最新的报告正文。 */
    Optional<String> findLatestByThread(long ownerId, String threadId);

    /** 保存默认通过状态的最新报告。 */
    void saveLatest(long ownerId, String threadId, String report);

    /** 保存任务生成的最新报告及评审结果。 */
    void saveLatest(long ownerId, String threadId, long taskId, String report, String reviewStatus, String critique);

    /** 保存带快照指纹和复用来源的报告版本，并返回持久化报告标识。 */
    long saveLatest(
            long ownerId,
            String threadId,
            long taskId,
            String report,
            String reviewStatus,
            String critique,
            Long snapshotId,
            String dataSnapshotHash,
            String generationContextHash,
            Long reusedFromReportId
    );

    /** 查询同用户下可安全复用的报告。 */
    Optional<ReusableReportRecord> findReusable(long ownerId, String generationContextHash);

    /** 查询指定任务已经生成的报告。 */
    Optional<ReusableReportRecord> findByTask(long ownerId, long taskId);
}
