package com.zzy.finsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 表示文档上传和切片入库结果。
 * @param status 当前状态。
 * @param fileCount 上传文件数量。
 * @param chunksStored 已写入的分片数量。
 * @param message 提示信息。
 */
public record UploadResponse(
        String status,
        @JsonProperty("file_count") int fileCount,
        @JsonProperty("chunks_stored") int chunksStored,
        String message
) {
}
