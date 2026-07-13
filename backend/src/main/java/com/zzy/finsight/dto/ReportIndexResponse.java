package com.zzy.finsight.dto;

/**
 * 表示报告加入知识库的结果。
 * @param reportId 报告标识。
 * @param chunksStored 已写入的分片数量。
 * @param status 当前状态。
 */
public record ReportIndexResponse(
        long reportId,
        int chunksStored,
        String status
) {
}
