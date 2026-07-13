package com.zzy.finsight.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface CheckpointMapper {
    default void save(String threadId, long taskId, Object state) {
        insert(threadId, taskId, state, LocalDateTime.now());
    }

    int insert(
            @Param("threadId") String threadId,
            @Param("taskId") long taskId,
            @Param("state") Object state,
            @Param("createdAt") LocalDateTime createdAt
    );
}
