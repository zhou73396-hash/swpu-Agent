package com.swpuagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.swpuagent.entity.CustomerBehavior;
import com.swpuagent.mapper.CustomerBehaviorMapper;
import com.swpuagent.service.CustomerBehaviorService;
import org.springframework.stereotype.Service;

@Service
public class CustomerBehaviorServiceImpl extends ServiceImpl<CustomerBehaviorMapper, CustomerBehavior> implements CustomerBehaviorService {
}
