package com.zzy.drai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UploadResponse(
        String status,
        @JsonProperty("file_count") int fileCount,
        @JsonProperty("chunks_stored") int chunksStored,
        String message
) {
}
