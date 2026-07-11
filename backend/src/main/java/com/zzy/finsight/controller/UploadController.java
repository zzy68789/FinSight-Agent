package com.zzy.finsight.controller;

import com.zzy.finsight.dto.UploadResponse;
import com.zzy.finsight.rag.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UploadController {
    private final RagService ragService;

    public UploadController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public UploadResponse upload(@RequestParam("files") List<MultipartFile> files) {
        if (files.size() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "一次最多只能上传 5 个文件");
        }
        int chunksStored = ragService.process(files);
        return new UploadResponse("success", files.size(), chunksStored, "知识库构建成功");
    }
}
