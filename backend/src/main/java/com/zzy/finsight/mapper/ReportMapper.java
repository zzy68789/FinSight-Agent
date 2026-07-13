package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.ReportRecord;
import com.zzy.finsight.domain.ReusableReportRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface ReportMapper {
    default void save(long ownerId, long taskId, String threadId, String content, String reviewStatus, String critique) {
        save(ownerId, taskId, threadId, content, reviewStatus, critique, null, null, null, null);
    }

    default long save(
            long ownerId,
            long taskId,
            String threadId,
            String content,
            String reviewStatus,
            String critique,
            Long snapshotId,
            String dataSnapshotHash,
            String generationContextHash,
            Long reusedFromReportId
    ) {
        int version = findLatestVersion(ownerId, threadId) + 1;
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("ownerId", ownerId);
        command.put("taskId", taskId);
        command.put("threadId", threadId);
        command.put("content", content);
        command.put("version", version);
        command.put("reviewStatus", reviewStatus);
        command.put("critique", critique);
        command.put("snapshotId", snapshotId);
        command.put("dataSnapshotHash", dataSnapshotHash);
        command.put("generationContextHash", generationContextHash);
        command.put("reusedFromReportId", reusedFromReportId);
        command.put("createdAt", LocalDateTime.now());
        insertReport(command);
        Number id = (Number) command.get("id");
        if (id == null) {
            throw new IllegalStateException("保存报告后未返回生成主键");
        }
        return id.longValue();
    }

    int findLatestVersion(@Param("ownerId") long ownerId, @Param("threadId") String threadId);

    int insertReport(Map<String, Object> command);

    Optional<ReusableReportRecord> findReusable(
            @Param("ownerId") long ownerId,
            @Param("generationContextHash") String generationContextHash
    );

    Optional<ReusableReportRecord> findByTask(@Param("ownerId") long ownerId, @Param("taskId") long taskId);

    Optional<ReportRecord> findReportByTask(@Param("ownerId") long ownerId, @Param("taskId") long taskId);

    Optional<String> findLatestByThread(@Param("ownerId") long ownerId, @Param("threadId") String threadId);

    List<ReportRecord> findReportsByThread(@Param("ownerId") long ownerId, @Param("threadId") String threadId);

    Optional<ReportRecord> findReportById(@Param("ownerId") long ownerId, @Param("reportId") long reportId);

    List<ReportRecord> findReports(
            @Param("ownerId") long ownerId,
            @Param("keyword") String keyword,
            @Param("favoriteOnly") boolean favoriteOnly
    );

    int updateFavorite(
            @Param("ownerId") long ownerId,
            @Param("reportId") long reportId,
            @Param("favorite") boolean favorite
    );

    default void softDelete(long ownerId, long reportId) {
        updateDeletedAt(ownerId, reportId, LocalDateTime.now());
    }

    int updateDeletedAt(
            @Param("ownerId") long ownerId,
            @Param("reportId") long reportId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    default void markIndexed(long ownerId, long reportId) {
        updateIndexedAt(ownerId, reportId, LocalDateTime.now());
    }

    int updateIndexedAt(
            @Param("ownerId") long ownerId,
            @Param("reportId") long reportId,
            @Param("indexedAt") LocalDateTime indexedAt
    );
}
