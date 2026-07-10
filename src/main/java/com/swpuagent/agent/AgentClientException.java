package com.swpuagent.agent;

import lombok.Getter;

@Getter
public class AgentClientException extends RuntimeException {

    private final AgentErrorCode errorCode;
    private final Integer httpStatus;

    public AgentClientException(AgentErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    public AgentClientException(AgentErrorCode errorCode, String message, Integer httpStatus) {
        this(errorCode, message, httpStatus, null);
    }

    private AgentClientException(AgentErrorCode errorCode, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
