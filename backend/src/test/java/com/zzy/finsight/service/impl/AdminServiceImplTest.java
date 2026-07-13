package com.zzy.finsight.service.impl;

import com.zzy.finsight.dto.AdminUserResponse;
import com.zzy.finsight.mapper.AdminAuditLogMapper;
import com.zzy.finsight.mapper.AdminMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceImplTest {

    @Test
    void updateUserRoleWritesAuditLog() {
        AdminMapper adminMapper = mock(AdminMapper.class);
        AdminAuditLogMapper auditLogMapper = mock(AdminAuditLogMapper.class);
        AdminServiceImpl adminService = new AdminServiceImpl(adminMapper, auditLogMapper);
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        AdminUserResponse updated = new AdminUserResponse(7L, "alice", "alice@example.com", "ADMIN", "ACTIVE", now, now, null);
        when(adminMapper.updateUserRole(7L, "ADMIN")).thenReturn(Optional.of(updated));

        AdminUserResponse response = adminService.updateUserRole(1L, 7L, "ADMIN");

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(auditLogMapper).save(1L, "UPDATE_USER_ROLE", "USER", 7L, "role=ADMIN");
    }

    @Test
    void updateUserStatusAndDeleteReportWriteAuditLogs() {
        AdminMapper adminMapper = mock(AdminMapper.class);
        AdminAuditLogMapper auditLogMapper = mock(AdminAuditLogMapper.class);
        AdminServiceImpl adminService = new AdminServiceImpl(adminMapper, auditLogMapper);
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        AdminUserResponse disabled = new AdminUserResponse(7L, "alice", "alice@example.com", "USER", "DISABLED", now, now, null);
        when(adminMapper.updateUserStatus(7L, "DISABLED")).thenReturn(Optional.of(disabled));

        AdminUserResponse response = adminService.updateUserStatus(1L, 7L, "DISABLED");
        adminService.deleteReport(1L, 21L);

        assertThat(response.status()).isEqualTo("DISABLED");
        verify(auditLogMapper).save(1L, "UPDATE_USER_STATUS", "USER", 7L, "status=DISABLED");
        verify(adminMapper).softDeleteReport(21L);
        verify(auditLogMapper).save(1L, "DELETE_REPORT", "REPORT", 21L, "softDelete=true");
    }

    @Test
    void systemHealthReportsTushareConfigurationStatus() {
        AdminMapper adminMapper = mock(AdminMapper.class);
        AdminAuditLogMapper auditLogMapper = mock(AdminAuditLogMapper.class);
        AdminServiceImpl configured = new AdminServiceImpl(
                adminMapper,
                auditLogMapper,
                null,
                null,
                null,
                "",
                "",
                "",
                true,
                "tushare-token"
        );
        AdminServiceImpl missing = new AdminServiceImpl(
                adminMapper,
                auditLogMapper,
                null,
                null,
                null,
                "",
                "",
                "",
                true,
                ""
        );

        assertThat(configured.systemHealth().components()).containsEntry("tushare", "CONFIGURED");
        assertThat(missing.systemHealth().components()).containsEntry("tushare", "MISSING");
    }
}
