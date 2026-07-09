package com.zzy.drai;

import com.zzy.drai.auth.AuthService;
import com.zzy.drai.auth.UserContext;
import com.zzy.drai.dto.ClearResponse;
import com.zzy.drai.financial.FinancialSnapshotRepository;
import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.AdminAuditLogRepository;
import com.zzy.drai.repository.AdminRepository;
import com.zzy.drai.repository.AppUserRepository;
import com.zzy.drai.repository.CheckpointRepository;
import com.zzy.drai.repository.ReportRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                "drai.auth.enabled=false"
        }
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

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    AppUserRepository appUserRepository;

    @MockitoBean
    AdminRepository adminRepository;

    @MockitoBean
    AdminAuditLogRepository adminAuditLogRepository;

    @MockitoBean
    FinancialSnapshotRepository financialSnapshotRepository;

    @BeforeEach
    void setUp() {
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskRepository.create(org.mockito.ArgumentMatchers.eq(7L), anyString(), anyString(), anyString())).thenReturn(1L);
        when(reportRepository.findLatestByThread(org.mockito.ArgumentMatchers.eq(7L), anyString())).thenReturn(Optional.empty());
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

}
