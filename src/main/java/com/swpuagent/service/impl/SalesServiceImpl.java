package com.swpuagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.swpuagent.entity.Sales;
import com.swpuagent.mapper.SalesMapper;
import com.swpuagent.service.SalesService;
import org.springframework.stereotype.Service;

@Service
public class SalesServiceImpl extends ServiceImpl<SalesMapper, Sales> implements SalesService {
}
