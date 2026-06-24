package com.zzy.drai;

import com.zzy.drai.dto.ClearResponse;
import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.CheckpointRepository;
import com.zzy.drai.repository.ReportRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration"
)
class ApplicationSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockitoBean
    ResearchTaskRepository taskRepository;

    @MockitoBean
    AgentStepLogRepository stepLogRepository;

    @MockitoBean
    CheckpointRepository checkpointRepository;

    @MockitoBean
    ReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        when(taskRepository.create(anyString(), anyString(), anyString())).thenReturn(1L);
        when(reportRepository.findLatestByThread(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void healthEndpointReportsJavaBackend() {
        @SuppressWarnings("unchecked")
        Map<String, String> response = restTemplate.getForObject("http://localhost:" + port + "/", Map.class);

        assertThat(response).containsEntry("status", "running");
        assertThat(response).containsEntry("backend", "java");
        assertThat(response).containsEntry("workflow", "langgraph4j");
    }

    @Test
    void clearEndpointKeepsFrontendCompatibleResponse() {
        ClearResponse response = restTemplate.postForObject("http://localhost:" + port + "/api/clear", null, ClearResponse.class);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("知识库已重置");
    }

    @Test
    void chatEndpointStreamsFrontendCompatibleSteps() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("""
                {
                  "query": "AI Agent 技术趋势",
                  "search_mode": "hybrid",
                  "thread_id": "test-thread"
                }
                """, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat",
                request,
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"step\":\"planner\"");
        assertThat(response.getBody()).contains("\"step\":\"researcher\"");
        assertThat(response.getBody()).contains("\"step\":\"writer\"");
        assertThat(response.getBody()).contains("\"step\":\"reviewer\"");
        assertThat(response.getBody()).contains("[DONE]");
    }

    @Test
    void chatEndpointRoutesFollowUpRevisionToRefiner() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String threadId = "test-refine-thread";

        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat",
                new HttpEntity<>("""
                        {
                          "query": "AI Agent 技术趋势",
                          "search_mode": "hybrid",
                          "thread_id": "%s"
                        }
                        """.formatted(threadId), headers),
                String.class
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat",
                new HttpEntity<>("""
                        {
                          "query": "修改上一版报告，把第一段改成更适合简历项目描述",
                          "search_mode": "hybrid",
                          "thread_id": "%s"
                        }
                        """.formatted(threadId), headers),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"step\":\"refiner\"");
        assertThat(response.getBody()).doesNotContain("\"step\":\"planner\"");
        assertThat(response.getBody()).contains("[DONE]");
    }
}
