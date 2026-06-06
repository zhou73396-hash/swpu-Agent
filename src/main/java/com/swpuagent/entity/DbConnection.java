package com.swpuagent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DbConnection {
    private Long id;
    private Long userId;
    private String name;
    private String dbType;       // MYSQL, POSTGRESQL
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String encryptedPassword;
    private Boolean isActive;
    private LocalDateTime lastTestedAt;
    private String testStatus;   // UNTESTED, SUCCESS, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
