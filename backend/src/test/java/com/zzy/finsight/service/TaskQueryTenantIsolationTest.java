package com.zzy.finsight.service;

import com.zzy.finsight.domain.ResearchTaskRecord;
import com.zzy.finsight.domain.ReportRecord;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;
import com.zzy.finsight.rag.RagService;
import com.zzy.finsight.repository.AgentStepLogRepository;
import com.zzy.finsight.repository.ReportRepository;
import com.zzy.finsight.repository.ResearchTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskQueryTenantIsolationTest {

    @Test
    void listTasksUsesOwnerScopedRepositoryMethods() {
        ResearchTaskRepository taskRepository = mock(ResearchTaskRepository.class);
        TaskQueryService service = new TaskQueryService(
                taskRepository,
                mock(AgentStepLogRepository.class),
                mock(ReportRepository.class),
                mock(RagService.class)
        );
        LocalDateTime now = LocalDateTime.now();
        when(taskRepository.findPage(7L, 1, 10, "COMPLETED", "agent"))
                .thenReturn(List.of(new ResearchTaskRecord(1L, "thread-1", "agent", "hybrid", "COMPLETED", 0, now, now)));
        when(taskRepository.count(7L, "COMPLETED", "agent")).thenReturn(1L);

        PageResponse<TaskSummaryResponse> page = service.listTasks(7L, 1, 10, "COMPLETED", "agent");

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).singleElement().extracting(TaskSummaryResponse::threadId).isEqualTo("thread-1");
        verify(taskRepository).findPage(7L, 1, 10, "COMPLETED", "agent");
        verify(taskRepository).count(7L, "COMPLETED", "agent");
    }

    @Test
    void indexReportToKnowledgeBaseUsesOwnerScopedReport() {
        ResearchTaskRepository taskRepository = mock(ResearchTaskRepository.class);
        ReportRepository reportRepository = mock(ReportRepository.class);
        RagService ragService = mock(RagService.class);
        TaskQueryService service = new TaskQueryService(
                taskRepository,
                mock(AgentStepLogRepository.class),
                reportRepository,
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
        when(reportRepository.findReportById(7L, 21L)).thenReturn(java.util.Optional.of(report));
        when(ragService.indexText("report-thread-1-v2.md", "# Report\nRAG reusable content")).thenReturn(2);

        ReportIndexResponse response = service.indexReportToKnowledgeBase(7L, 21L);

        assertThat(response.reportId()).isEqualTo(21L);
        assertThat(response.chunksStored()).isEqualTo(2);
        verify(reportRepository).findReportById(7L, 21L);
        verify(reportRepository).markIndexed(7L, 21L);
    }
}
