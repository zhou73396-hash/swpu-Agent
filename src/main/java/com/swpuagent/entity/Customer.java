package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("customer")
public class Customer {

    private Long userId;

    private String username;

    private String registrationDate;

    private String country;

    private Long age;

    private String gender;

    private Double totalSpent;

    private Long orderCount;
}