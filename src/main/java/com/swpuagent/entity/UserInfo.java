package com.swpuagent.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserInfo {
    private Long id;
    private String userName;
    private String email;
    private String role;
    private Integer age;
    private String country;
    private BigDecimal salary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
