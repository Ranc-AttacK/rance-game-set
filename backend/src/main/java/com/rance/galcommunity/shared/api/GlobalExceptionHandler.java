package com.rance.galcommunity.shared.api;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent("_global", error.getDefaultMessage()));
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status())
                .headers(headers)
                .body(ApiResponse.error(code, "参数校验失败", fields));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = mapStatus(statusCode.value());
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ApiResponse.error(code, messageFor(code)));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code, "参数校验失败", fields));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation traceId={} cause={}",
                MDC.get("traceId"), ex.getMostSpecificCause().getMessage());
        ErrorCode code = ErrorCode.CONFLICT;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, "数据冲突，请稍后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled error traceId={}", MDC.get("traceId"), ex);
        ErrorCode code = ErrorCode.INTERNAL;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, "服务器内部错误"));
    }

    private ErrorCode mapStatus(int status) {
        return switch (status) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status >= 500 ? ErrorCode.INTERNAL : ErrorCode.VALIDATION_FAILED;
        };
    }

    private String messageFor(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED -> "请求参数不合法";
            case UNAUTHENTICATED -> "请先登录";
            case FORBIDDEN -> "没有权限";
            case NOT_FOUND -> "资源不存在";
            case METHOD_NOT_ALLOWED -> "请求方法不支持";
            case FILE_TOO_LARGE -> "文件过大";
            case UNSUPPORTED_MEDIA -> "不支持的媒体类型";
            case RATE_LIMITED -> "请求过于频繁";
            case INTERNAL -> "服务器内部错误";
            default -> "请求失败";
        };
    }
}
