package com.zzy.finsight.component.evaluation;

import java.util.List;

/**
 * 表示一个带分级相关性标注的冻结检索问题。
 * @param caseId 检索样例标识。
 * @param query 检索问题。
 * @param corpus 冻结检索语料。
 */
public record RetrievalCaseFixture(
        String caseId,
        String query,
        List<RetrievalCorpusFixture> corpus
) {
    public RetrievalCaseFixture {
        corpus = corpus == null ? List.of() : List.copyOf(corpus);
    }
}
