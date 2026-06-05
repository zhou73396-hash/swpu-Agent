package com.swpuagent.common.exception;

public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(409, message);
    }
}
