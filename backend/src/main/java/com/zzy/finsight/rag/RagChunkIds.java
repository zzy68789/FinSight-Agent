package com.zzy.finsight.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 为 RAG 文档分片生成跨运行稳定的内容标识。
 */
public final class RagChunkIds {
    private RagChunkIds() {
    }

    /** 根据标准化来源、分片序号和正文生成 SHA-256 标识。 */
    public static String generate(String source, int chunkIndex, String content) {
        String payload = normalize(source) + "\n" + chunkIndex + "\n" + normalize(content);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
