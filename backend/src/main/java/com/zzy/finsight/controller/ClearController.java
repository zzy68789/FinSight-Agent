package com.zzy.finsight.controller;

import com.zzy.finsight.dto.ClearResponse;
import com.zzy.finsight.rag.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClearController {
    private final RagService ragService;

    public ClearController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/clear")
    public ClearResponse clear() {
        ragService.clear();
        return new ClearResponse("success", "知识库已重置");
    }
}
