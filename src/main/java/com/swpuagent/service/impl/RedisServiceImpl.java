package com.swpuagent.service.impl;

import com.swpuagent.service.RedisService;
import com.swpuagent.service.VerificationCodeConsumeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final long CODE_TTL_SECONDS = 60;
    private static final DefaultRedisScript<Long> CONSUME_CODE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then
                return -1
            end
            if stored ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    @Override
    public void storeCode(String prefix, String email, String code) {
        String key = prefix + ":" + email;
        stringRedisTemplate.opsForValue().set(key, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public String getCode(String prefix, String email) {
        String key = prefix + ":" + email;
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public VerificationCodeConsumeResult consumeCode(
            String prefix,
            String email,
            String submittedCode
    ) {
        String key = prefix + ":" + email;
        Long result = stringRedisTemplate.execute(
                CONSUME_CODE_SCRIPT,
                List.of(key),
                submittedCode
        );
        if (Long.valueOf(1L).equals(result)) {
            return VerificationCodeConsumeResult.SUCCESS;
        }
        if (Long.valueOf(0L).equals(result)) {
            return VerificationCodeConsumeResult.INVALID;
        }
        if (Long.valueOf(-1L).equals(result)) {
            return VerificationCodeConsumeResult.EXPIRED;
        }
        throw new IllegalStateException("Unexpected Redis verification code result: " + result);
    }

    @Override
    public void deleteCode(String prefix, String email) {
        String key = prefix + ":" + email;
        stringRedisTemplate.delete(key);
    }
}
