package com.swpuagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swpuagent.entity.Products;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductsMapper extends BaseMapper<Products> {
}
