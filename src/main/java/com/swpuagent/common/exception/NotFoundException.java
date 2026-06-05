package com.swpuagent.common.exception;

public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(404, message);
    }
}
