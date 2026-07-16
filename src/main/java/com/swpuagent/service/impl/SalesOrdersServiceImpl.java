package com.swpuagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.swpuagent.entity.SalesOrders;
import com.swpuagent.mapper.SalesOrdersMapper;
import com.swpuagent.service.SalesOrdersService;
import org.springframework.stereotype.Service;

@Service
public class SalesOrdersServiceImpl extends ServiceImpl<SalesOrdersMapper, SalesOrders> implements SalesOrdersService {
}
