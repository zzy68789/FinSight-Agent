package com.zzy.finsight.service;

import com.zzy.finsight.domain.ReusableReportRecord;

import java.util.Optional;

public interface ReportService {
    Optional<String> findLatestByThread(long ownerId, String threadId);

    void saveLatest(long ownerId, String threadId, String report);

    void saveLatest(long ownerId, String threadId, long taskId, String report, String reviewStatus, String critique);

    void saveLatest(
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

    Optional<ReusableReportRecord> findReusable(long ownerId, String generationContextHash);

    Optional<ReusableReportRecord> findByTask(long ownerId, long taskId);
}
