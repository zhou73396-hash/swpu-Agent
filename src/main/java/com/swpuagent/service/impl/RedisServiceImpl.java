package com.swpuagent.service.impl;

import com.swpuagent.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final long CODE_TTL_SECONDS = 60;

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
    public void deleteCode(String prefix, String email) {
        String key = prefix + ":" + email;
        stringRedisTemplate.delete(key);
    }
}
