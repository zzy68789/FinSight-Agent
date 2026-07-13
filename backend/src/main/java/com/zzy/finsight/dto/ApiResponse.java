package com.zzy.finsight.dto;

/**
 * 封装统一的 API 响应结构。
 * @param code 响应状态码。
 * @param message 提示信息。
 * @param data 响应数据。
 */
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
