package com.swpuagent.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh Token cannot be empty") String refreshToken
) {
}
