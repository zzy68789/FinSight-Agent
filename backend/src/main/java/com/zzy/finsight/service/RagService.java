package com.zzy.finsight.service;

import com.zzy.finsight.rag.RagDocument;
import com.zzy.finsight.rag.RagRetrievalResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 定义 RAG 文档入库、检索和索引维护服务。
 */
public interface RagService {
    /** 解析上传文档并重建当前索引。 */
    int process(List<MultipartFile> files);

    /** 检索与问题最相关的文档片段。 */
    List<RagDocument> retrieve(String query, int topK);

    /** 检索文档并返回完整追踪信息。 */
    RagRetrievalResult retrieveWithTrace(String query, int topK);

    /** 将指定来源的纯文本切片后写入索引。 */
    int indexText(String source, String text);

    /** 清空当前 RAG 索引。 */
    void clear();
}
