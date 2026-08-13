package com.smartfitagent.controller;

import com.smartfitagent.ai.decorator.RateLimitingAiClientDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器（Spring AOP + MVC 异常处理）
 *
 * 将所有未处理的异常统一转换为 JSON 格式的错误响应，
 * 避免 Spring 默认的 HTML 错误页面泄露给 API 客户端。
 *
 * 错误响应格式：
 * {
 *   "timestamp": "2026-01-01T12:00:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "缺少必填项: heightCm",
 *   "path": "/api/user"
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("参数错误: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        log.error("业务状态错误: {}", ex.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitingAiClientDecorator.RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(
            RateLimitingAiClientDecorator.RateLimitExceededException ex,
            WebRequest request) {
        log.warn("请求频率超限: {}", ex.getMessage());
        Map<String, Object> body = buildErrorBody(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
        body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            Exception ex, WebRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持", request);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex,
            WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "缺少请求参数: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, WebRequest request) {
        log.error("未处理异常: {}", ex.getMessage(), ex);
        String userMessage = ex.getMessage() != null
                ? ex.getMessage()
                : "服务内部错误，请稍后重试";
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, userMessage, request);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status).body(buildErrorBody(status, message, request));
    }

    private Map<String, Object> buildErrorBody(HttpStatus status, String message, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? message : "未知错误");
        String path = request.getDescription(false).replace("uri=", "");
        body.put("path", path);
        return body;
    }
}
