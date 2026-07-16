package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sales")
public class Sales {

    private String year;

    private Double totalSales;

    private Long totalOrders;

    private Long totalQuantitySold;

    private String category;

    private Double averageOrderValue;
}
