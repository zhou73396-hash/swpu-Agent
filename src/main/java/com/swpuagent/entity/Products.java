package com.swpuagent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("products")
public class Products {

    private Long productId;

    private String productName;

    private String category;

    private Double price;

    private Long stock;

    private Long salesVolume;

    private Double averageRating;
}
