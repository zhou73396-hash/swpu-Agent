package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("sales_orders")
public class SalesOrders {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private LocalDate orderDate;

    private String productName;

    private String category;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private String customerCity;
}
