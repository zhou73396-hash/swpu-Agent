package com.swpuagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpireMs;
    private final long refreshTokenExpireMs;
    private final String secret;

    public JwtUtil(
            @Value("${jwt.secret-key}") String secret,
            @Value("${jwt.access-token-expire-minutes}") long accessMinutes,
            @Value("${jwt.refresh-token-expire-days}") long refreshDays) {
        this.secret = secret;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireMs = accessMinutes * 60 * 1000;
        this.refreshTokenExpireMs = refreshDays * 24 * 60 * 60 * 1000;
    }

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_KEY is required. Set it via environment variable.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY must be at least 32 characters (current: " + secret.length() + ")");
        }
        log.info("JWT secret key validated ({} chars)", secret.length());
    }

    /** Generate JWT access token */
    public String generateAccessToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .claims(Map.of("role", role))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpireMs))
                .signWith(key)
                .compact();
    }

    /** Generate opaque refresh token (random hex, not JWT) */
    public String generateRefreshToken() {
        byte[] bytes = new byte[64];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(128);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Parse and validate JWT access token, return claims or null if invalid */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract user ID from valid token */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return Long.parseLong(claims.getSubject());
    }

    /** Extract role from valid token */
    public String getRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("role", String.class);
    }

    /** Check if token is valid (not expired, correctly signed) */
    public boolean isValid(String token) {
        return parseToken(token) != null;
    }
}
