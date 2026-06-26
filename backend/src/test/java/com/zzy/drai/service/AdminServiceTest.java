package com.zzy.drai.service;

import com.zzy.drai.dto.AdminUserResponse;
import com.zzy.drai.repository.AdminAuditLogRepository;
import com.zzy.drai.repository.AdminRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    @Test
    void updateUserRoleWritesAuditLog() {
        AdminRepository adminRepository = mock(AdminRepository.class);
        AdminAuditLogRepository auditLogRepository = mock(AdminAuditLogRepository.class);
        AdminService adminService = new AdminService(adminRepository, auditLogRepository);
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        AdminUserResponse updated = new AdminUserResponse(7L, "alice", "alice@example.com", "ADMIN", "ACTIVE", now, now, null);
        when(adminRepository.updateUserRole(7L, "ADMIN")).thenReturn(Optional.of(updated));

        AdminUserResponse response = adminService.updateUserRole(1L, 7L, "ADMIN");

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(auditLogRepository).save(1L, "UPDATE_USER_ROLE", "USER", 7L, "role=ADMIN");
    }

    @Test
    void updateUserStatusAndDeleteReportWriteAuditLogs() {
        AdminRepository adminRepository = mock(AdminRepository.class);
        AdminAuditLogRepository auditLogRepository = mock(AdminAuditLogRepository.class);
        AdminService adminService = new AdminService(adminRepository, auditLogRepository);
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        AdminUserResponse disabled = new AdminUserResponse(7L, "alice", "alice@example.com", "USER", "DISABLED", now, now, null);
        when(adminRepository.updateUserStatus(7L, "DISABLED")).thenReturn(Optional.of(disabled));

        AdminUserResponse response = adminService.updateUserStatus(1L, 7L, "DISABLED");
        adminService.deleteReport(1L, 21L);

        assertThat(response.status()).isEqualTo("DISABLED");
        verify(auditLogRepository).save(1L, "UPDATE_USER_STATUS", "USER", 7L, "status=DISABLED");
        verify(adminRepository).softDeleteReport(21L);
        verify(auditLogRepository).save(1L, "DELETE_REPORT", "REPORT", 21L, "softDelete=true");
    }
}
