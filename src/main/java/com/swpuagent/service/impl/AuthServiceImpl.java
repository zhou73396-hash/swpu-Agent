package com.swpuagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swpuagent.agent.AgentClient;
import com.swpuagent.common.auth.AuthErrorCode;
import com.swpuagent.common.auth.AuthException;
import com.swpuagent.dto.response.TokenPairResponse;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.security.JwtUtil;
import com.swpuagent.service.AuthService;
import com.swpuagent.service.RedisService;
import com.swpuagent.service.UserInfoService;
import com.swpuagent.service.VerificationCodeConsumeResult;
import com.swpuagent.service.auth.RefreshTokenStore;
import com.swpuagent.utils.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RedisService redisService;
    private final AgentClient agentClient;
    private final JwtUtil jwtUtil;
    private final UserInfoService userInfoService;
    private final RefreshTokenStore refreshTokenStore;

    private static final String LOGIN_CODE_PREFIX = "login:code";
    private static final String REGISTER_CODE_PREFIX = "register:code";

    @Override
    public Result<Void> sendLoginCode(String email) {
        String code = RandomUtil.randomNumbers(4);
        redisService.storeCode(LOGIN_CODE_PREFIX, email, code);
        String message = String.format("send login verification code %s to email %s", code, email);
        log.info("AuthFlow action=SEND_LOGIN_CODE phase=system_agent_call email={" +
                "}", maskEmail(email));
        JSONObject agentResult = agentClient.systemChat(message,email);
        log.info("AuthFlow action=SEND_LOGIN_CODE phase=system_agent_result email={} resultCode={}",
                maskEmail(email), agentResult.getStr("code"));
        if (!"200".equals(agentResult.getStr("code"))) {
            redisService.deleteCode(LOGIN_CODE_PREFIX, email);
            throw new AuthException(AuthErrorCode.AUTH_CODE_SEND_FAILED,
                    agentResult.getStr("msg", AuthErrorCode.AUTH_CODE_SEND_FAILED.getMessage()));
        }

        return Result.success();
    }

    @Override
    public Result<Void> sendRegisterCode(String email) {


        String message = String.format("send register verification code to email %s", email);
        log.info("AuthFlow action=SEND_REGISTER_CODE phase=system_agent_call email={}", maskEmail(email));
        JSONObject agentResult = agentClient.systemChat(message,email);
        log.info("AuthFlow action=SEND_REGISTER_CODE phase=system_agent_result email={} resultCode={}",
                maskEmail(email), agentResult.getStr("code"));
        if (!"200".equals(agentResult.getStr("code"))) {
            redisService.deleteCode(REGISTER_CODE_PREFIX, email);
            throw new AuthException(AuthErrorCode.AUTH_CODE_SEND_FAILED,
                    agentResult.getStr("msg", AuthErrorCode.AUTH_CODE_SEND_FAILED.getMessage()));
        }

        return Result.success();
    }

    @Override
    public Result<TokenPairResponse> login(String email, String code) {
        consumeVerificationCode(LOGIN_CODE_PREFIX, email, code);

        log.info("AuthFlow action=LOGIN_VALIDATE phase=redis_verified email={}", maskEmail(email));

        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getEmail, email);
        UserInfo user = userInfoService.getOne(wrapper);
        if (user == null || user.getId() == null) {
            throw new AuthException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }

        String role = user.getRole() == null || user.getRole().isBlank() ? "user" : user.getRole();
        TokenPairResponse tokenPair = issueTokenPair(user.getId(), user.getEmail(), role);
        return Result.success(tokenPair);
    }

    @Override
    public Result<TokenPairResponse> refresh(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);
        Long userId = parseUserId(claims);
        String oldJti = claims.getId();
        UserInfo user = userInfoService.getById(userId);
        if (user == null) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        String role = user.getRole() == null || user.getRole().isBlank() ? "user" : user.getRole();
        String newJti = UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateAccessToken(userId, user.getEmail(), role);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, newJti);
        boolean rotated = refreshTokenStore.rotate(
                userId,
                oldJti,
                refreshToken,
                newJti,
                newRefreshToken,
                jwtUtil.getRefreshTokenExpiresInSeconds()
        );
        if (!rotated) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
        }

        return Result.success(new TokenPairResponse(
                newAccessToken,
                newRefreshToken,
                jwtUtil.getAccessTokenExpiresInSeconds(),
                jwtUtil.getRefreshTokenExpiresInSeconds()
        ));
    }

    @Override
    public Result<Void> logout(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);
        Long userId = parseUserId(claims);
        if (!refreshTokenStore.revoke(userId, claims.getId(), refreshToken)) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
        }
        return Result.success();
    }

    private TokenPairResponse issueTokenPair(Long userId, String email, String role) {
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(userId, email, role);
        String refreshToken = jwtUtil.generateRefreshToken(userId, jti);
        long refreshExpiresIn = jwtUtil.getRefreshTokenExpiresInSeconds();
        refreshTokenStore.save(userId, jti, refreshToken, refreshExpiresIn);

        return new TokenPairResponse(
                accessToken,
                refreshToken,
                jwtUtil.getAccessTokenExpiresInSeconds(),
                refreshExpiresIn
        );
    }

    @Override
    public Result<Void> register(String email, String code, String userName) {
        consumeVerificationCode(REGISTER_CODE_PREFIX, email, code);

        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getEmail, email);
        if (userInfoService.getOne(wrapper) != null) {
            throw new AuthException(AuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED);
        }

        UserInfo user = new UserInfo();
        user.setUserName(userName);
        user.setEmail(email);
        user.setRole("user");
        if (!userInfoService.save(user)) {
            throw new AuthException(AuthErrorCode.AUTH_USER_CREATE_FAILED);
        }
        log.info("AuthFlow action=REGISTER phase=db_insert email={} userName={}", maskEmail(email), userName);

        return Result.success();
    }

    private void consumeVerificationCode(String prefix, String email, String submittedCode) {
        VerificationCodeConsumeResult result =
                redisService.consumeCode(prefix, email, submittedCode);
        if (result == VerificationCodeConsumeResult.EXPIRED) {
            throw new AuthException(AuthErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
        }
        if (result == VerificationCodeConsumeResult.INVALID) {
            throw new AuthException(AuthErrorCode.AUTH_VERIFICATION_CODE_INVALID);
        }
        if (result != VerificationCodeConsumeResult.SUCCESS) {
            throw new IllegalStateException("Unexpected verification code consumption result: " + result);
        }
    }

    private Claims parseRefreshToken(String refreshToken) {
        try {
            Claims claims = jwtUtil.parseToken(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class))
                    || claims.getSubject() == null
                    || claims.getId() == null
                    || claims.getId().isBlank()) {
                throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
            }
            return claims;
        } catch (ExpiredJwtException ex) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
    }

    private Long parseUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
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
