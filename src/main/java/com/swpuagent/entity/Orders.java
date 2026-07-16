package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("orders")
public class Orders {

    private Long orderId;

    private Long userId;

    private String orderDate;

    private Long productId;

    private Long quantity;

    private Double totalAmount;

    private String paymentMethod;

    private String orderStatus;
}
