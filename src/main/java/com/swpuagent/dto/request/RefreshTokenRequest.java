package com.swpuagent.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token cannot be empty") String refreshToken
) {
}
