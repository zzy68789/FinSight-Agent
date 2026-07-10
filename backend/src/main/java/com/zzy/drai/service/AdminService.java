package com.zzy.drai.service;

import com.zzy.drai.dto.AdminReportResponse;
import com.zzy.drai.dto.AdminSystemHealthResponse;
import com.zzy.drai.dto.AdminTaskResponse;
import com.zzy.drai.dto.AdminUserResponse;
import com.zzy.drai.dto.AgentStepLogResponse;
import com.zzy.drai.repository.AdminAuditLogRepository;
import com.zzy.drai.repository.AdminRepository;
import com.zzy.drai.repository.AgentStepLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminService {
    private final AdminRepository adminRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final AgentStepLogRepository stepLogRepository;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final String llmApiKey;
    private final String tavilyApiKey;
    private final String chromaBaseUrl;
    private final boolean tushareEnabled;
    private final String tushareApiKey;

    public AdminService(AdminRepository adminRepository, AdminAuditLogRepository auditLogRepository) {
        this(adminRepository, auditLogRepository, null, null, null, "", "", "", false, "");
    }

    @Autowired
    public AdminService(
            AdminRepository adminRepository,
            AdminAuditLogRepository auditLogRepository,
            AgentStepLogRepository stepLogRepository,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${drai.llm.api-key:}") String llmApiKey,
            @Value("${drai.tavily.api-key:}") String tavilyApiKey,
            @Value("${drai.chroma.base-url:}") String chromaBaseUrl,
            @Value("${drai.market.tushare.enabled:false}") boolean tushareEnabled,
            @Value("${drai.market.tushare.api-key:}") String tushareApiKey
    ) {
        this.adminRepository = adminRepository;
        this.auditLogRepository = auditLogRepository;
        this.stepLogRepository = stepLogRepository;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.llmApiKey = llmApiKey;
        this.tavilyApiKey = tavilyApiKey;
        this.chromaBaseUrl = chromaBaseUrl;
        this.tushareEnabled = tushareEnabled;
        this.tushareApiKey = tushareApiKey;
    }

    public List<AdminUserResponse> listUsers(String keyword) {
        return adminRepository.findUsers(keyword);
    }

    public AdminUserResponse updateUserRole(long adminUserId, long userId, String role) {
        String normalizedRole = normalizeRole(role);
        AdminUserResponse user = adminRepository.updateUserRole(userId, normalizedRole)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogRepository.save(adminUserId, "UPDATE_USER_ROLE", "USER", userId, "role=" + normalizedRole);
        return user;
    }

    public AdminUserResponse updateUserStatus(long adminUserId, long userId, String status) {
        String normalizedStatus = normalizeStatus(status);
        AdminUserResponse user = adminRepository.updateUserStatus(userId, normalizedStatus)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogRepository.save(adminUserId, "UPDATE_USER_STATUS", "USER", userId, "status=" + normalizedStatus);
        return user;
    }

    public List<AdminTaskResponse> listTasks(String status, Long ownerId, String keyword) {
        return adminRepository.findTasks(normalizeOptional(status), ownerId, normalizeOptional(keyword));
    }

    public List<AgentStepLogResponse> getTaskLogs(long taskId) {
        if (stepLogRepository == null) {
            return List.of();
        }
        return stepLogRepository.findByTaskId(taskId).stream()
                .map(log -> new AgentStepLogResponse(
                        log.id(),
                        log.taskId(),
                        log.stepName(),
                        log.inputSnapshot(),
                        log.outputSnapshot(),
                        log.status(),
                        log.errorMessage(),
                        log.attemptNo(),
                        log.durationMs(),
                        log.createdAt()
                ))
                .toList();
    }

    public List<AdminReportResponse> listReports(Long ownerId, String keyword) {
        return adminRepository.findReports(ownerId, normalizeOptional(keyword));
    }

    public void deleteReport(long adminUserId, long reportId) {
        adminRepository.softDeleteReport(reportId);
        auditLogRepository.save(adminUserId, "DELETE_REPORT", "REPORT", reportId, "softDelete=true");
    }

    public AdminSystemHealthResponse systemHealth() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("mysql", mysqlStatus());
        components.put("redis", redisStatus());
        components.put("chroma", isBlank(chromaBaseUrl) ? "MISSING" : "CONFIGURED");
        components.put("llm", isBlank(llmApiKey) ? "MISSING" : "CONFIGURED");
        components.put("tavily", isBlank(tavilyApiKey) ? "MISSING" : "CONFIGURED");
        components.put("tushare", marketDataStatus());
        return new AdminSystemHealthResponse(components);
    }

    private String marketDataStatus() {
        if (!tushareEnabled) {
            return "DISABLED";
        }
        return isBlank(tushareApiKey) ? "MISSING" : "CONFIGURED";
    }

    private String mysqlStatus() {
        if (jdbcTemplateProvider == null) {
            return "UNKNOWN";
        }
        try {
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
            if (jdbcTemplate == null) {
                return "UNKNOWN";
            }
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1 ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String redisStatus() {
        if (redisTemplateProvider == null) {
            return "UNKNOWN";
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
                return "UNKNOWN";
            }
            try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                return connection.ping() != null ? "UP" : "DOWN";
            }
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String normalizeRole(String role) {
        String normalized = normalizeRequired(role, "role");
        if (!List.of("USER", "ADMIN").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeRequired(status, "status");
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
