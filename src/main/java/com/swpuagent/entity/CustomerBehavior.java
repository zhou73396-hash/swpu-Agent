package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("customer_behavior")
public class CustomerBehavior {

    private Long userId;

    private Long productId;

    private String action;

    private String actionDate;

    private String device;
}