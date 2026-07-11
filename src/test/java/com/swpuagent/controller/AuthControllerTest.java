package com.swpuagent.controller;

import com.swpuagent.common.GlobalExceptionHandler;
import com.swpuagent.common.auth.AuthErrorCode;
import com.swpuagent.common.auth.AuthException;
import com.swpuagent.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Test
    void invalidVerificationCodeShouldReturnHttp401AndUnifiedError() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login("user@example.com", "9999"))
                .thenThrow(new AuthException(AuthErrorCode.AUTH_VERIFICATION_CODE_INVALID));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"9999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_CODE_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedRefreshRequestShouldReturnHttp400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(mock(AuthService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedAuthenticationFailureShouldReturnUnifiedHttp500() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login("user@example.com", "1234"))
                .thenThrow(new RuntimeException("Redis command timed out"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"1234\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
