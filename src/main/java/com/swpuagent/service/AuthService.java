package com.swpuagent.service;

import com.swpuagent.common.exception.ValidationException;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserInfoMapper userInfoMapper;
    private final VerificationCodeService verificationCodeService;

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
        // TODO: Integrate Agent → send_email tool for real email delivery
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
        // TODO: Integrate Agent → send_email tool for real email delivery
        verificationCodeService.saveRegisterCode(email, code);
        log.info("Registration verification code for {}: {}", email, code);
    }

    /**
     * Login with email + verification code.
     * Returns a placeholder token (JWT integration planned).
     */
    public String loginWithCode(String email, String code) {
        boolean valid = verificationCodeService.validateLoginCode(email, code);
        if (!valid) {
            throw new ValidationException("验证码错误或已过期");
        }
        UserInfo user = userInfoMapper.findByEmail(email);
        log.info("User {} logged in via verification code", email);
        return "token_" + user.getId();
    }

    /**
     * Register a new user with email + verification code + username.
     * Returns a placeholder token (JWT integration planned).
     */
    public String register(String email, String code, String userName) {
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
        return "token_" + userInfo.getId();
    }
}
