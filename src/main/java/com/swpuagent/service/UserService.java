package com.swpuagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.utils.Result;
import com.swpuagent.vo.SendLoginCodeRequest;

public interface UserService extends IService<UserInfo> {
    Result<Integer> sendLoginCode(SendLoginCodeRequest request);

}
