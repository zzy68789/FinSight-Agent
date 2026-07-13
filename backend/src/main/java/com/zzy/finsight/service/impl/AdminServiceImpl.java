package com.zzy.finsight.service.impl;

import com.zzy.finsight.dto.AdminReportResponse;
import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.dto.AdminTaskResponse;
import com.zzy.finsight.dto.AdminUserResponse;
import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.mapper.AdminAuditLogMapper;
import com.zzy.finsight.mapper.AdminMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.SystemMapper;
import com.zzy.finsight.service.AdminService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {
    private final AdminMapper adminMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final ObjectProvider<SystemMapper> systemMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final String llmApiKey;
    private final String tavilyApiKey;
    private final String chromaBaseUrl;
    private final boolean tushareEnabled;
    private final String tushareApiKey;

    public AdminServiceImpl(AdminMapper adminMapper, AdminAuditLogMapper auditLogMapper) {
        this(adminMapper, auditLogMapper, null, null, null, "", "", "", false, "");
    }

    @Autowired
    public AdminServiceImpl(
            AdminMapper adminMapper,
            AdminAuditLogMapper auditLogMapper,
            AgentStepLogMapper stepLogMapper,
            ObjectProvider<SystemMapper> systemMapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${finsight.llm.api-key:}") String llmApiKey,
            @Value("${finsight.tavily.api-key:}") String tavilyApiKey,
            @Value("${finsight.chroma.base-url:}") String chromaBaseUrl,
            @Value("${finsight.market.tushare.enabled:false}") boolean tushareEnabled,
            @Value("${finsight.market.tushare.api-key:}") String tushareApiKey
    ) {
        this.adminMapper = adminMapper;
        this.auditLogMapper = auditLogMapper;
        this.stepLogMapper = stepLogMapper;
        this.systemMapperProvider = systemMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.llmApiKey = llmApiKey;
        this.tavilyApiKey = tavilyApiKey;
        this.chromaBaseUrl = chromaBaseUrl;
        this.tushareEnabled = tushareEnabled;
        this.tushareApiKey = tushareApiKey;
    }

    public List<AdminUserResponse> listUsers(String keyword) {
        return adminMapper.findUsers(keyword);
    }

    public AdminUserResponse updateUserRole(long adminUserId, long userId, String role) {
        String normalizedRole = normalizeRole(role);
        AdminUserResponse user = adminMapper.updateUserRole(userId, normalizedRole)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogMapper.save(adminUserId, "UPDATE_USER_ROLE", "USER", userId, "role=" + normalizedRole);
        return user;
    }

    public AdminUserResponse updateUserStatus(long adminUserId, long userId, String status) {
        String normalizedStatus = normalizeStatus(status);
        AdminUserResponse user = adminMapper.updateUserStatus(userId, normalizedStatus)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogMapper.save(adminUserId, "UPDATE_USER_STATUS", "USER", userId, "status=" + normalizedStatus);
        return user;
    }

    public List<AdminTaskResponse> listTasks(String status, Long ownerId, String keyword) {
        return adminMapper.findTasks(normalizeOptional(status), ownerId, normalizeOptional(keyword));
    }

    public List<AgentStepLogResponse> getTaskLogs(long taskId) {
        if (stepLogMapper == null) {
            return List.of();
        }
        return stepLogMapper.findByTaskId(taskId).stream()
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
        return adminMapper.findReports(ownerId, normalizeOptional(keyword));
    }

    public void deleteReport(long adminUserId, long reportId) {
        adminMapper.softDeleteReport(reportId);
        auditLogMapper.save(adminUserId, "DELETE_REPORT", "REPORT", reportId, "softDelete=true");
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
        if (systemMapperProvider == null) {
            return "UNKNOWN";
        }
        try {
            SystemMapper systemMapper = systemMapperProvider.getIfAvailable();
            if (systemMapper == null) {
                return "UNKNOWN";
            }
            return systemMapper.ping() == 1 ? "UP" : "DOWN";
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
