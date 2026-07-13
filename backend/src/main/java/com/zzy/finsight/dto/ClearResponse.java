package com.zzy.finsight.dto;

/**
 * 表示清理操作结果。
 * @param status 当前状态。
 * @param message 提示信息。
 */
public record ClearResponse(String status, String message) {
}
