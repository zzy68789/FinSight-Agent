package com.zzy.finsight.rag;

import java.util.List;

/**
 * 记录单条候选文档的检索评分轨迹。
 * @param chunkId 稳定分片标识。
 * @param source 数据来源。
 * @param content 候选文档正文。
 * @param keywordScore 关键词检索分数。
 * @param vectorScore 向量检索分数。
 * @param fusionScore 混合检索融合分数。
 * @param rank 检索排名。
 * @param channels 命中的检索通道。
 */
public record RagRetrievalTraceEntry(
        String chunkId,
        String source,
        String content,
        double keywordScore,
        double vectorScore,
        double fusionScore,
        int rank,
        List<String> channels
) {
    public RagRetrievalTraceEntry {
        chunkId = chunkId == null || chunkId.isBlank()
                ? RagChunkIds.generate(source, -1, content)
                : chunkId;
        channels = channels == null ? List.of() : List.copyOf(channels);
    }

    /** 保留原有追踪构造方式并根据正文生成兼容标识。 */
    public RagRetrievalTraceEntry(
            String source,
            String content,
            double keywordScore,
            double vectorScore,
            double fusionScore,
            int rank,
            List<String> channels
    ) {
        this(null, source, content, keywordScore, vectorScore, fusionScore, rank, channels);
    }
}
