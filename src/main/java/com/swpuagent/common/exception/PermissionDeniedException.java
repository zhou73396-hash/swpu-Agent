package com.swpuagent.common.exception;

public class PermissionDeniedException extends AppException {
    public PermissionDeniedException(String message) {
        super(403, message);
    }
}
