package com.swpuagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.swpuagent.entity.Products;
import com.swpuagent.mapper.ProductsMapper;
import com.swpuagent.service.ProductsService;
import org.springframework.stereotype.Service;

@Service
public class ProductsServiceImpl extends ServiceImpl<ProductsMapper, Products> implements ProductsService {
}
