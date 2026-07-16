package com.swpuagent.common.auth;

import java.time.LocalDateTime;

public record ApiErrorResponse(String code, String message, Object data, LocalDateTime timestamp) {

    public static ApiErrorResponse of(AuthErrorCode errorCode, String message) {
        return new ApiErrorResponse(errorCode.name(), message, null, LocalDateTime.now());
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null, LocalDateTime.now());
    }
}
