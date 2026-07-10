package com.swpuagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = jwtUtil(1_800_000L, 604_800_000L);
    }

    @Test
    void accessTokenShouldContainIdentityAndTypeClaims() {
        String token = jwtUtil.generateAccessToken(42L, "user@example.com", "manager");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("manager");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    void refreshTokenShouldContainUniqueSessionClaims() {
        String token = jwtUtil.generateRefreshToken(42L, "session-id");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getId()).isEqualTo("session-id");
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void expiredTokenShouldBeRejected() {
        JwtUtil expiredJwtUtil = jwtUtil(-1_000L, -1_000L);
        String token = expiredJwtUtil.generateRefreshToken(42L, "session-id");

        assertThatThrownBy(() -> expiredJwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenShouldBeRejected() {
        String token = jwtUtil.generateAccessToken(42L, "user@example.com", "user");
        String[] parts = token.split("\\.");
        int index = parts[1].length() / 2;
        char replacement = parts[1].charAt(index) == 'a' ? 'b' : 'a';
        parts[1] = parts[1].substring(0, index) + replacement + parts[1].substring(index + 1);
        String tampered = String.join(".", parts);

        assertThatThrownBy(() -> jwtUtil.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    private JwtUtil jwtUtil(long accessExpiration, long refreshExpiration) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secretKey", "test-jwt-secret-key-with-at-least-32-bytes");
        ReflectionTestUtils.setField(util, "accessTokenExpiration", accessExpiration);
        ReflectionTestUtils.setField(util, "refreshTokenExpiration", refreshExpiration);
        return util;
    }
}
