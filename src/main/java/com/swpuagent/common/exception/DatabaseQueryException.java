package com.swpuagent.common.exception;

public class DatabaseQueryException extends AppException {
    public DatabaseQueryException(String message) {
        super(422, message);
    }
}
