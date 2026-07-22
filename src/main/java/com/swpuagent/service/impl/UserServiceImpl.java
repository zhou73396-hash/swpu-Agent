package com.swpuagent.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.swpuagent.dto.SendEmailVO;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import com.swpuagent.service.UserService;
import com.swpuagent.utils.Result;
import com.swpuagent.vo.SendLoginCodeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserService {

    @Value("${agent.base-url:http://localhost:8000}")
    private String agentBaseUrl;

    @Override
    public Result<Integer> sendLoginCode(SendLoginCodeRequest request) {
        String result = HttpUtil.post(agentBaseUrl, request.getEmail());
        SendEmailVO res = JSONUtil.toBean(result, SendEmailVO.class);
        QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
        if ("200".equals(res.getCode())) {
            wrapper.eq("email", request.getEmail());
            UserInfo user = this.getOne(wrapper);
            if (user != null) {
                return Result.success(1);
            }
        }
        return Result.error(500, "Failed to send login code");
    }
}
