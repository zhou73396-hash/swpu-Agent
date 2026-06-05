package com.swpuagent.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final int statusCode;

    public AppException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
