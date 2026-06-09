package com.swpuagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import com.swpuagent.service.UserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {
}
