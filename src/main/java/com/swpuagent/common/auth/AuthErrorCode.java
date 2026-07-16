package com.swpuagent.common.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {
    AUTH_ACCESS_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Access Token is missing"),
    AUTH_ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access Token has expired"),
    AUTH_ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Access Token is invalid"),
    AUTH_REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh Token has expired"),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh Token is invalid"),
    AUTH_REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh Token has been revoked or reused"),
    AUTH_VERIFICATION_CODE_EXPIRED(HttpStatus.UNAUTHORIZED, "Verification code expired or not sent"),
    AUTH_VERIFICATION_CODE_INVALID(HttpStatus.UNAUTHORIZED, "Invalid verification code"),
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not registered"),
    AUTH_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email already registered"),
    AUTH_CODE_SEND_FAILED(HttpStatus.BAD_GATEWAY, "Failed to send verification code"),
    AUTH_USER_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user");

    private final HttpStatus httpStatus;
    private final String message;
}
