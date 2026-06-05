package com.swpuagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CODE_TTL_SECONDS = 300; // 5 minutes
    private static final String LOGIN_CODE_PREFIX = "login_code:";
    private static final String REGISTER_CODE_PREFIX = "register_code:";

    public String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public void saveLoginCode(String email, String code) {
        String key = LOGIN_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Login code saved for {}: key={}, ttl={}s", email, key, CODE_TTL_SECONDS);
    }

    public void saveRegisterCode(String email, String code) {
        String key = REGISTER_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Register code saved for {}: key={}, ttl={}s", email, key, CODE_TTL_SECONDS);
    }

    public boolean validateLoginCode(String email, String inputCode) {
        String key = LOGIN_CODE_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(inputCode)) {
            redisTemplate.delete(key);
            log.debug("Login code validated and deleted for {}", email);
            return true;
        }
        log.debug("Login code validation failed for {}", email);
        return false;
    }

    public boolean validateRegisterCode(String email, String inputCode) {
        String key = REGISTER_CODE_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(inputCode)) {
            redisTemplate.delete(key);
            log.debug("Register code validated and deleted for {}", email);
            return true;
        }
        log.debug("Register code validation failed for {}", email);
        return false;
    }
}
