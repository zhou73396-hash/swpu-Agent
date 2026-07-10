package com.swpuagent.service.auth;

public interface RefreshTokenStore {

    void save(Long userId, String jti, String token, long ttlSeconds);

    boolean rotate(Long userId, String oldJti, String oldToken,
                   String newJti, String newToken, long ttlSeconds);

    boolean revoke(Long userId, String jti, String token);
}
