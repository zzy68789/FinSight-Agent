package com.zzy.drai.service;

import com.zzy.drai.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final ConcurrentMap<String, String> latestReportByThread = new ConcurrentHashMap<>();

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public Optional<String> findLatestByThread(String threadId) {
        Optional<String> persisted = reportRepository.findLatestByThread(threadId);
        return persisted.isPresent() ? persisted : Optional.ofNullable(latestReportByThread.get(threadId));
    }

    public void saveLatest(String threadId, String report) {
        saveLatest(threadId, 0L, report, "PASS", "");
    }

    public void saveLatest(String threadId, long taskId, String report, String reviewStatus, String critique) {
        if (threadId != null && report != null && !report.isBlank()) {
            latestReportByThread.put(threadId, report);
            reportRepository.save(taskId, threadId, report, reviewStatus, critique);
        }
    }
}
