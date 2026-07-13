package com.zzy.finsight.service.impl;

import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.domain.ReusableReportRecord;
import com.zzy.finsight.service.ReportService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 实现报告保存、查询和同用户复用业务。
 */
@Service
public class ReportServiceImpl implements ReportService {
    private final ReportMapper reportMapper;
    private final ConcurrentMap<String, String> latestReportByThread = new ConcurrentHashMap<>();

    public ReportServiceImpl(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public Optional<String> findLatestByThread(long ownerId, String threadId) {
        Optional<String> persisted = reportMapper.findLatestByThread(ownerId, threadId);
        return persisted.isPresent() ? persisted : Optional.ofNullable(latestReportByThread.get(cacheKey(ownerId, threadId)));
    }

    public void saveLatest(long ownerId, String threadId, String report) {
        saveLatest(ownerId, threadId, 0L, report, "PASS", "");
    }

    public void saveLatest(long ownerId, String threadId, long taskId, String report, String reviewStatus, String critique) {
        saveLatest(ownerId, threadId, taskId, report, reviewStatus, critique, null, null, null, null);
    }

    public void saveLatest(
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
    ) {
        if (threadId != null && report != null && !report.isBlank()) {
            latestReportByThread.put(cacheKey(ownerId, threadId), report);
            reportMapper.save(ownerId, taskId, threadId, report, reviewStatus, critique,
                    snapshotId, dataSnapshotHash, generationContextHash, reusedFromReportId);
        }
    }

    public Optional<ReusableReportRecord> findReusable(long ownerId, String generationContextHash) {
        return reportMapper.findReusable(ownerId, generationContextHash);
    }

    public Optional<ReusableReportRecord> findByTask(long ownerId, long taskId) {
        return reportMapper.findByTask(ownerId, taskId);
    }

    private String cacheKey(long ownerId, String threadId) {
        return ownerId + ":" + threadId;
    }
}
