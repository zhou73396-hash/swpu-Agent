package com.swpuagent.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DbConnectionRequest {
    @NotBlank(message = "连接名称不能为空")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    private String dbType;       // MYSQL or POSTGRESQL

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @Min(1) @Max(65535)
    private Integer port = 3306;

    @NotBlank(message = "数据库名不能为空")
    private String databaseName;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;     // plain text, encrypted before storage
}
