package com.swpuagent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatSession {
    private Long id;
    private Long userId;
    private Long dbConnectionId;
    private String title;
    private String status;    // ACTIVE, ARCHIVED, DELETED
    private Integer messageCount;
    private Integer totalTokensUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
