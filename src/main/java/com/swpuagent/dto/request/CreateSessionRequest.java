package com.swpuagent.dto.request;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private Long dbConnectionId;
    private String title;
}
