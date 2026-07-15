package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.ClearResponse;
import com.zzy.finsight.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供清理 RAG 文档的接口。
 */
@RestController
@RequestMapping("/api")
public class ClearController {
    private final RagService ragService;
    private final UserContext userContext;

    public ClearController(RagService ragService, UserContext userContext) {
        this.ragService = ragService;
        this.userContext = userContext;
    }

    /** 清空当前 RAG 知识库。 */
    @PostMapping("/clear")
    public ClearResponse clear() {
        ragService.clear(userContext.currentUserId());
        return new ClearResponse("success", "知识库已重置");
    }
}
