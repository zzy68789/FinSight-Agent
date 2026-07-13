package com.zzy.finsight.service.impl;

import com.zzy.finsight.domain.ResearchTaskRecord;
import com.zzy.finsight.domain.ReportRecord;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;
import com.zzy.finsight.rag.RagService;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskQueryServiceImplTenantIsolationTest {

    @Test
    void listTasksUsesOwnerScopedMapperMethods() {
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        TaskQueryServiceImpl service = new TaskQueryServiceImpl(
                taskMapper,
                mock(AgentStepLogMapper.class),
                mock(ReportMapper.class),
                mock(RagService.class)
        );
        LocalDateTime now = LocalDateTime.now();
        when(taskMapper.findPage(7L, 1, 10, "COMPLETED", "agent"))
                .thenReturn(List.of(new ResearchTaskRecord(1L, "thread-1", "agent", "hybrid", "COMPLETED", 0, now, now)));
        when(taskMapper.count(7L, "COMPLETED", "agent")).thenReturn(1L);

        PageResponse<TaskSummaryResponse> page = service.listTasks(7L, 1, 10, "COMPLETED", "agent");

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).singleElement().extracting(TaskSummaryResponse::threadId).isEqualTo("thread-1");
        verify(taskMapper).findPage(7L, 1, 10, "COMPLETED", "agent");
        verify(taskMapper).count(7L, "COMPLETED", "agent");
    }

    @Test
    void indexReportToKnowledgeBaseUsesOwnerScopedReport() {
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        ReportMapper reportMapper = mock(ReportMapper.class);
        RagService ragService = mock(RagService.class);
        TaskQueryServiceImpl service = new TaskQueryServiceImpl(
                taskMapper,
                mock(AgentStepLogMapper.class),
                reportMapper,
                ragService
        );
        LocalDateTime now = LocalDateTime.now();
        ReportRecord report = new ReportRecord(
                21L,
                1L,
                "thread-1",
                "# Report\nRAG reusable content",
                2,
                "PASS",
                "",
                now,
                false,
                null
        );
        when(reportMapper.findReportById(7L, 21L)).thenReturn(java.util.Optional.of(report));
        when(ragService.indexText("report-thread-1-v2.md", "# Report\nRAG reusable content")).thenReturn(2);

        ReportIndexResponse response = service.indexReportToKnowledgeBase(7L, 21L);

        assertThat(response.reportId()).isEqualTo(21L);
        assertThat(response.chunksStored()).isEqualTo(2);
        verify(reportMapper).findReportById(7L, 21L);
        verify(reportMapper).markIndexed(7L, 21L);
    }
}
