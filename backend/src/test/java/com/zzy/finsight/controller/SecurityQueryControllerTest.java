package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.dto.stock.SecurityCandidateResponse;
import com.zzy.finsight.dto.stock.SecurityMatchType;
import com.zzy.finsight.dto.stock.SecurityPreviewResponse;
import com.zzy.finsight.service.AuthService;
import com.zzy.finsight.service.SecurityQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SecurityQueryController.class, properties = "finsight.auth.enabled=false")
class SecurityQueryControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SecurityQueryService securityQueryService;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @Test
    void searchesSecurityByName() throws Exception {
        when(securityQueryService.search("贵州茅台")).thenReturn(List.of(new SecurityCandidateResponse(
                "600519", "600519.SH", "贵州茅台", "食品饮料",
                StockAssetType.EQUITY, SecurityMatchType.EXACT_NAME, true
        )));

        mockMvc.perform(get("/api/securities/search").param("query", "贵州茅台"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fullCode").value("600519.SH"))
                .andExpect(jsonPath("$.data[0].matchType").value("EXACT_NAME"));
    }

    @Test
    void previewsCanonicalSecurityIdentity() throws Exception {
        when(securityQueryService.preview("600519")).thenReturn(new SecurityPreviewResponse(
                "600519", "600519", "600519.SH", "贵州茅台", "食品饮料",
                StockAssetType.EQUITY, true, true, "已识别证券"
        ));

        mockMvc.perform(get("/api/securities/600519/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true))
                .andExpect(jsonPath("$.data.nameResolved").value(true))
                .andExpect(jsonPath("$.data.companyName").value("贵州茅台"));
    }
}
