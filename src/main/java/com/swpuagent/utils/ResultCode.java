package com.swpuagent.utils;

public enum ResultCode {
    SUCCESS(200, "success"),
    FAIL(400, "operation failed"),
    VALIDATE_FAILED(400, "validation failed"),
    ERROR(500, "internal server error");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
