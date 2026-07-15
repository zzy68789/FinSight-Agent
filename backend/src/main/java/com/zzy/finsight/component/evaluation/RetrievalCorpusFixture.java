package com.zzy.finsight.component.evaluation;

/**
 * 表示冻结检索语料中的一个分片及其人工相关性等级。
 * @param source 文档来源。
 * @param chunkIndex 分片序号。
 * @param content 分片正文。
 * @param relevanceGrade 相关性等级，零表示不相关。
 */
public record RetrievalCorpusFixture(
        String source,
        int chunkIndex,
        String content,
        int relevanceGrade
) {
}
