package com.swpuagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swpuagent.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
