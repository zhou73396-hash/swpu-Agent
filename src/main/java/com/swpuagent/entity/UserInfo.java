package com.swpuagent.entity;

import lombok.Data;

@Data
public class UserInfo {
    private Long id;
    private String userName;
    private String email;
    private String role;
    private String password;
}
