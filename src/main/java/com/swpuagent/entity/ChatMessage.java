package com.swpuagent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;        // USER, ASSISTANT, SYSTEM, TOOL
    private String content;
    private String messageType; // TEXT, SQL, CHART, THINKING, ERROR
    private String metadata;    // JSON string
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
