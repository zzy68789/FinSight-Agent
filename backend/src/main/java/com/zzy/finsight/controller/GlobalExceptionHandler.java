package com.zzy.finsight.controller;

import com.zzy.finsight.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一转换控制器抛出的业务异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** 将带 HTTP 状态的业务异常转换为统一响应。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        int statusCode = exception.getStatusCode().value();
        String message = exception.getReason() == null ? "request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(statusCode, message));
    }
}
