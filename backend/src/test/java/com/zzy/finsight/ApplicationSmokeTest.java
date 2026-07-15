package com.zzy.finsight;

import com.zzy.finsight.service.AuthService;
import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ClearResponse;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.AdminAuditLogMapper;
import com.zzy.finsight.mapper.AdminMapper;
import com.zzy.finsight.mapper.AppUserMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration",
                "finsight.auth.enabled=false"
        }
)
class ApplicationSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockitoBean
    ResearchTaskMapper taskMapper;

    @MockitoBean
    AgentStepLogMapper stepLogMapper;

    @MockitoBean
    CheckpointMapper checkpointMapper;

    @MockitoBean
    ReportMapper reportMapper;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    AppUserMapper appUserMapper;

    @MockitoBean
    AdminMapper adminMapper;

    @MockitoBean
    AdminAuditLogMapper adminAuditLogMapper;

    @MockitoBean
    FinancialSnapshotMapper financialSnapshotMapper;

    @BeforeEach
    void setUp() {
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskMapper.create(org.mockito.ArgumentMatchers.eq(7L), anyString(), anyString(), anyString())).thenReturn(1L);
        when(reportMapper.findLatestByThread(org.mockito.ArgumentMatchers.eq(7L), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void healthEndpointReportsJavaBackend() {
        @SuppressWarnings("unchecked")
        Map<String, String> response = restTemplate.getForObject("http://localhost:" + port + "/", Map.class);

        assertThat(response).containsEntry("status", "running");
        assertThat(response).containsEntry("backend", "java");
        assertThat(response).containsEntry("workflow", "stock-report-pipeline");
    }

    @Test
    void clearEndpointKeepsFrontendCompatibleResponse() {
        ClearResponse response = restTemplate.postForObject("http://localhost:" + port + "/api/clear", null, ClearResponse.class);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("知识库已重置");
    }

    @Test
    void genericChatEndpointIsNotExposed() {
        var response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat",
                Map.of("query", "通用研究"),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

}
