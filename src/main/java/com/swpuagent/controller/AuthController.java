package com.swpuagent.controller;

import com.swpuagent.dto.request.LoginRequest;
import com.swpuagent.dto.request.LogoutRequest;
import com.swpuagent.dto.request.RefreshTokenRequest;
import com.swpuagent.dto.request.RegisterRequest;
import com.swpuagent.dto.request.SendCodeRequest;
import com.swpuagent.dto.response.TokenPairResponse;
import com.swpuagent.service.AuthService;
import com.swpuagent.utils.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Send login verification code.
     * Public endpoint — no JWT required.
     */
    @PostMapping("/send_code")
    public Result<Void> sendLoginCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendLoginCode(request.getEmail());
    }

    /**
     * Send registration verification code.
     * Public endpoint — no JWT required.
     */
    @PostMapping("/send_register_code")
    public Result<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendRegisterCode(request.getEmail());
    }

    /**
     * Login with email + verification code.
     * Public endpoint — returns JWT tokens.
     */
    @PostMapping("/login")
    public Result<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getCode());
    }

    @PostMapping("/refresh")
    public Result<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutRequest request) {
        return authService.logout(request.refreshToken());
    }

    /**
     * Register new user.
     * Public endpoint — no JWT required.
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.getEmail(), request.getCode(), request.getUserName());
    }
}
