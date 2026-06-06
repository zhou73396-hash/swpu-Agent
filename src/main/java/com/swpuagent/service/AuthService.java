package com.swpuagent.service;

import com.swpuagent.common.exception.ValidationException;
import com.swpuagent.dto.response.LoginResponse;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import com.swpuagent.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserInfoMapper userInfoMapper;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtil jwtUtil;

    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 30 * 60; // 30 minutes

    /**
     * Send login verification code.
     * Rule: email MUST already exist in user_info table.
     */
    public void sendLoginCode(String email) {
        UserInfo user = userInfoMapper.findByEmail(email);
        if (user == null) {
            throw new ValidationException("邮箱未注册");
        }
        String code = verificationCodeService.generateCode();
        verificationCodeService.saveLoginCode(email, code);
        log.info("Login verification code for {}: {}", email, code);
    }

    /**
     * Send registration verification code.
     * Rule: email MUST NOT already exist in user_info table.
     */
    public void sendRegisterCode(String email) {
        int count = userInfoMapper.countByEmail(email);
        if (count > 0) {
            throw new ValidationException("邮箱已注册，请直接登录");
        }
        String code = verificationCodeService.generateCode();
        verificationCodeService.saveRegisterCode(email, code);
        log.info("Registration verification code for {}: {}", email, code);
    }

    /**
     * Login with email + verification code.
     * Returns JWT access token + refresh token.
     */
    public LoginResponse loginWithCode(String email, String code) {
        boolean valid = verificationCodeService.validateLoginCode(email, code);
        if (!valid) {
            throw new ValidationException("验证码错误或已过期");
        }
        UserInfo user = userInfoMapper.findByEmail(email);
        log.info("User {} (id={}) logged in via verification code", email, user.getId());

        return LoginResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(user.getId(), user.getRole()))
                .refreshToken(jwtUtil.generateRefreshToken())
                .tokenType("Bearer")
                .expiresIn(ACCESS_TOKEN_EXPIRE_SECONDS)
                .userId(user.getId())
                .userName(user.getUserName())
                .role(user.getRole())
                .build();
    }

    /**
     * Register a new user with email + verification code + username.
     * Returns JWT access token + refresh token.
     */
    public LoginResponse register(String email, String code, String userName) {
        boolean valid = verificationCodeService.validateRegisterCode(email, code);
        if (!valid) {
            throw new ValidationException("验证码错误或已过期");
        }
        // Race-condition guard: double-check email uniqueness
        if (userInfoMapper.countByEmail(email) > 0) {
            throw new ValidationException("邮箱已被注册");
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setEmail(email);
        userInfo.setUserName(userName);
        userInfo.setRole("USER");
        userInfoMapper.insert(userInfo);
        log.info("New user registered: id={}, email={}", userInfo.getId(), email);

        return LoginResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(userInfo.getId(), userInfo.getRole()))
                .refreshToken(jwtUtil.generateRefreshToken())
                .tokenType("Bearer")
                .expiresIn(ACCESS_TOKEN_EXPIRE_SECONDS)
                .userId(userInfo.getId())
                .userName(userInfo.getUserName())
                .role(userInfo.getRole())
                .build();
    }
}
