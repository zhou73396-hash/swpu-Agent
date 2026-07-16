package com.swpuagent.service;

import com.swpuagent.dto.response.TokenPairResponse;
import com.swpuagent.utils.Result;

public interface AuthService {

    Result<Void> sendLoginCode(String email);

    Result<Void> sendRegisterCode(String email);

    Result<TokenPairResponse> login(String email, String code);

    Result<TokenPairResponse> refresh(String refreshToken);

    Result<Void> logout(String refreshToken);

    Result<Void> register(String email, String code, String userName);
}
