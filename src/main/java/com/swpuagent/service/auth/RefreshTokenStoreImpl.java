package com.swpuagent.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements RefreshTokenStore {

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void save(Long userId, String jti, String token, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key(userId, jti), hash(token), ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean rotate(Long userId, String oldJti, String oldToken,
                          String newJti, String newToken, long ttlSeconds) {
        Long result = stringRedisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(userId, oldJti), key(userId, newJti)),
                hash(oldToken),
                hash(newToken),
                Long.toString(ttlSeconds)
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean revoke(Long userId, String jti, String token) {
        Long result = stringRedisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(key(userId, jti)),
                hash(token)
        );
        return Long.valueOf(1L).equals(result);
    }

    private String key(Long userId, String jti) {
        return "auth:refresh:" + userId + ":" + jti;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
