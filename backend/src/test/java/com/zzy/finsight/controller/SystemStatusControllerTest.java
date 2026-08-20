package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.service.AdminService;
import com.zzy.finsight.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SystemStatusController.class, properties = "finsight.auth.enabled=false")
class SystemStatusControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminService adminService;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @Test
    void exposesSafeSystemReadinessToAuthenticatedWorkspace() throws Exception {
        when(adminService.systemHealth()).thenReturn(new AdminSystemHealthResponse(Map.of(
                "mysql", "UP",
                "redis", "DOWN",
                "llm", "CONFIGURED"
        )));

        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components.mysql").value("UP"))
                .andExpect(jsonPath("$.data.components.redis").value("DOWN"));
    }
}
