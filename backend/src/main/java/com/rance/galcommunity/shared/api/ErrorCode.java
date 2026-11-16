package com.rance.galcommunity.shared.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(40001, HttpStatus.BAD_REQUEST),
    INVALID_CURSOR(40002, HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(40101, HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, HttpStatus.UNAUTHORIZED),
    TOKEN_REUSED(40103, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED(40302, HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND),
    CONFLICT(40901, HttpStatus.CONFLICT),
    FILE_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    BUSINESS_RULE(42201, HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL(50001, HttpStatus.INTERNAL_SERVER_ERROR),
    UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final HttpStatus status;

    ErrorCode(int code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
