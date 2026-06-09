package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_info")
public class UserInfo {

    @TableId
    private Integer id;

    private String userName;

    private String email;

    private String role;
}
