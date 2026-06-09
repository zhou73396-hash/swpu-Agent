package com.swpuagent.service;

import com.swpuagent.dto.response.LoginResponse;
import com.swpuagent.utils.Result;

public interface AuthService {

    Result<Void> sendLoginCode(String email);

    Result<Void> sendRegisterCode(String email);

    Result<LoginResponse> login(String email, String code);

    Result<Void> register(String email, String code, String userName);
}
