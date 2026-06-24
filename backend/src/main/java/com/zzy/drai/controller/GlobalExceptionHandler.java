package com.zzy.drai.controller;

import com.zzy.drai.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        int statusCode = exception.getStatusCode().value();
        String message = exception.getReason() == null ? "request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(statusCode, message));
    }
}
