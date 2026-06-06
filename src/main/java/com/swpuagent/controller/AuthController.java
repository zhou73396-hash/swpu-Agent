package com.swpuagent.controller;

import com.swpuagent.dto.request.LoginRequest;
import com.swpuagent.dto.request.RegisterRequest;
import com.swpuagent.dto.request.SendCodeRequest;
import com.swpuagent.dto.response.ApiResponse;
import com.swpuagent.dto.response.LoginResponse;
import com.swpuagent.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Send login verification code.
     * POST /api/auth/send_code
     */
    @PostMapping("/send_code")
    public ApiResponse<Void> sendLoginCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendLoginCode(request.getEmail());
        return ApiResponse.success("发送成功", null);
    }

    /**
     * Send registration verification code.
     * POST /api/auth/send_register_code
     */
    @PostMapping("/send_register_code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendRegisterCode(request.getEmail());
        return ApiResponse.success("发送成功", null);
    }

    /**
     * Login with email + verification code.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse resp = authService.loginWithCode(request.getEmail(), request.getCode());
        return ApiResponse.success("登陆成功", resp);
    }

    /**
     * Register new user.
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse resp = authService.register(
                request.getEmail(), request.getCode(), request.getUserName());
        return ApiResponse.success("注册成功", resp);
    }
}
