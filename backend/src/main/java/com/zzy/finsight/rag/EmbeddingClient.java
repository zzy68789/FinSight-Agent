package com.zzy.finsight.rag;

import java.util.List;

/**
 * 定义文本向量化能力。
 */
public interface EmbeddingClient {
    /** 将文本转换为向量。 */
    List<Double> embed(String text);
}
