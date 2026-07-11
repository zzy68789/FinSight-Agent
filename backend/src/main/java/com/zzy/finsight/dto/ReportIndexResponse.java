package com.zzy.finsight.dto;

public record ReportIndexResponse(
        long reportId,
        int chunksStored,
        String status
) {
}
