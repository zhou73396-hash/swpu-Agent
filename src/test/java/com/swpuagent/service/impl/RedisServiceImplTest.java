package com.swpuagent.service.impl;

import com.swpuagent.service.VerificationCodeConsumeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RedisServiceImpl redisService;

    @BeforeEach
    void setUp() {
        redisService = new RedisServiceImpl(stringRedisTemplate);
    }

    @Test
    void matchingCodeShouldBeConsumed() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("login:code:user@example.com")),
                eq("1234")
        )).thenReturn(1L);

        VerificationCodeConsumeResult result =
                redisService.consumeCode("login:code", "user@example.com", "1234");

        assertThat(result).isEqualTo(VerificationCodeConsumeResult.SUCCESS);
    }

    @Test
    void mismatchedCodeShouldNotBeAccepted() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("login:code:user@example.com")),
                eq("9999")
        )).thenReturn(0L);

        VerificationCodeConsumeResult result =
                redisService.consumeCode("login:code", "user@example.com", "9999");

        assertThat(result).isEqualTo(VerificationCodeConsumeResult.INVALID);
    }

    @Test
    void missingCodeShouldBeReportedAsExpired() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("login:code:user@example.com")),
                eq("1234")
        )).thenReturn(-1L);

        VerificationCodeConsumeResult result =
                redisService.consumeCode("login:code", "user@example.com", "1234");

        assertThat(result).isEqualTo(VerificationCodeConsumeResult.EXPIRED);
    }

    @Test
    void unexpectedScriptResultShouldNotBecomeAuthenticationFailure() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("login:code:user@example.com")),
                eq("1234")
        )).thenReturn(null);

        assertThatThrownBy(() ->
                redisService.consumeCode("login:code", "user@example.com", "1234"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected Redis verification code result");
    }
}
