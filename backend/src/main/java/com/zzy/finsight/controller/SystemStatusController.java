package com.zzy.finsight.controller;

import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前登录用户可见的安全系统就绪状态。
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {
    private final AdminService adminService;

    public SystemStatusController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 查询数据库、缓存、检索和外部数据源的就绪状态。 */
    @GetMapping("/health")
    public ApiResponse<AdminSystemHealthResponse> health() {
        return ApiResponse.success(adminService.systemHealth());
    }
}
