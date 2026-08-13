package com.zzy.finsight.controller;

import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.stock.SecurityCandidateResponse;
import com.zzy.finsight.dto.stock.SecurityPreviewResponse;
import com.zzy.finsight.service.SecurityQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供证券代码或名称搜索和只读预解析接口。
 */
@RestController
@RequestMapping("/api/securities")
public class SecurityQueryController {
    private final SecurityQueryService securityQueryService;

    public SecurityQueryController(SecurityQueryService securityQueryService) {
        this.securityQueryService = securityQueryService;
    }

    /** 按代码或名称搜索证券候选。 */
    @GetMapping("/search")
    public ApiResponse<List<SecurityCandidateResponse>> search(@RequestParam String query) {
        return ApiResponse.success(securityQueryService.search(query));
    }

    /** 预解析证券代码并返回规范化标识。 */
    @GetMapping("/{ticker}/preview")
    public ApiResponse<SecurityPreviewResponse> preview(@PathVariable String ticker) {
        return ApiResponse.success(securityQueryService.preview(ticker));
    }
}
