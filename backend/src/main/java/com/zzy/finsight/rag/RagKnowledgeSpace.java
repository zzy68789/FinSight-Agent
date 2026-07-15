package com.zzy.finsight.rag;

/**
 * 表示彼此隔离的 RAG 知识空间。
 *
 * @param id 可持久化且可用于过滤的空间标识。
 */
public record RagKnowledgeSpace(String id) {
    public RagKnowledgeSpace {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("知识空间标识不能为空");
        }
        id = id.trim();
    }

    /** 根据业务用户标识创建独立知识空间。 */
    public static RagKnowledgeSpace forOwner(long ownerId) {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("用户标识必须为正数");
        }
        return new RagKnowledgeSpace("user-" + ownerId);
    }

    /** 创建用于离线评测等非用户流程的命名空间。 */
    public static RagKnowledgeSpace named(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("命名知识空间不能为空");
        }
        return new RagKnowledgeSpace("named-" + name.trim());
    }
}
