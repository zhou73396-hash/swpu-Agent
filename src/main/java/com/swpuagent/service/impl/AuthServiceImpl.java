package com.swpuagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swpuagent.agent.AgentClient;
import com.swpuagent.dto.response.LoginResponse;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.security.JwtUtil;
import com.swpuagent.service.AuthService;
import com.swpuagent.service.RedisService;
import com.swpuagent.service.UserInfoService;
import com.swpuagent.utils.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RedisService redisService;
    private final AgentClient agentClient;
    private final JwtUtil jwtUtil;
    private final UserInfoService userInfoService;

    @Value("${jwt.access-token-expiration:1800000}")
    private long accessTokenExpiration;

    private static final String LOGIN_CODE_PREFIX = "login:code";
    private static final String REGISTER_CODE_PREFIX = "register:code";

    @Override
    public Result<Void> sendLoginCode(String email) {
        // 1. Generate 4-digit verification code
        String code = RandomUtil.randomNumbers(4);

        // 2. Store in Redis with TTL 60s
        redisService.storeCode(LOGIN_CODE_PREFIX, email, code);

        // 3. Forward to Python SystemAgent to send email
        String message = String.format("send login verification code %s to email %s", code, email);
        log.info("AuthFlow action=SEND_LOGIN_CODE phase=system_agent_call email={" +
                "}", maskEmail(email));
        JSONObject agentResult = agentClient.systemChat(message,email);
        log.info("AuthFlow action=SEND_LOGIN_CODE phase=system_agent_result email={} resultCode={}",
                maskEmail(email), agentResult.getStr("code"));
        if (!"200".equals(agentResult.getStr("code"))) {
            redisService.deleteCode(LOGIN_CODE_PREFIX, email);
            return Result.error(500, agentResult.getStr("msg", "Failed to send login code"));
        }

        return Result.success();
    }

    @Override
    public Result<Void> sendRegisterCode(String email) {


        // 3. Forward to Python SystemAgent to send email
        String message = String.format("send register verification code to email %s", email);
        log.info("AuthFlow action=SEND_REGISTER_CODE phase=system_agent_call email={}", maskEmail(email));
        JSONObject agentResult = agentClient.systemChat(message,email);
        log.info("AuthFlow action=SEND_REGISTER_CODE phase=system_agent_result email={} resultCode={}",
                maskEmail(email), agentResult.getStr("code"));
        if (!"200".equals(agentResult.getStr("code"))) {
            redisService.deleteCode(REGISTER_CODE_PREFIX, email);
            return Result.error(500, agentResult.getStr("msg", "Failed to send register code"));
        }

        return Result.success();
    }

    @Override
    public Result<LoginResponse> login(String email, String code) {
        // 1. Verify code from Redis
        String storedCode = redisService.getCode(LOGIN_CODE_PREFIX, email);
        if (storedCode == null) {
            return Result.error(500, "Verification code expired or not sent");
        }
        if (!storedCode.equals(code)) {
            return Result.error(500, "Invalid verification code");
        }

        log.info("AuthFlow action=LOGIN_VALIDATE phase=redis_verified email={}", maskEmail(email));

        // 2. Delete used code
        redisService.deleteCode(LOGIN_CODE_PREFIX, email);

        // 3. Issue JWT tokens
        String accessToken = jwtUtil.generateAccessToken(email, "user");
        String refreshToken = jwtUtil.generateRefreshToken();

        LoginResponse loginResponse = LoginResponse.of(accessToken, refreshToken, accessTokenExpiration);
        return Result.success(loginResponse);
    }

    @Override
    public Result<Void> register(String email, String code, String userName) {
        // 1. Verify code from Redis
        String storedCode = redisService.getCode(REGISTER_CODE_PREFIX, email);
        if (storedCode == null) {
            return Result.error(500, "Verification code expired or not sent");
        }
        if (!storedCode.equals(code)) {
            return Result.error(500, "Invalid verification code");
        }

        // 2. Check if email already registered
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getEmail, email);
        if (userInfoService.getOne(wrapper) != null) {
            return Result.error(500, "Email already registered");
        }

        // 3. Insert user directly into shared database
        UserInfo user = new UserInfo();
        user.setUserName(userName);
        user.setEmail(email);
        user.setRole("user");
        userInfoService.save(user);
        log.info("AuthFlow action=REGISTER phase=db_insert email={} userName={}", maskEmail(email), userName);

        // 4. Delete used code
        redisService.deleteCode(REGISTER_CODE_PREFIX, email);

        return Result.success();
    }

    private String maskEmail(String email) {
        if (email == null) {
            return "<null>";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }

        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
