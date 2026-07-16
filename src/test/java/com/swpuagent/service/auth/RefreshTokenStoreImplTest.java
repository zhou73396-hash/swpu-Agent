package com.swpuagent.service.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenStoreImplTest {

    @Test
    void saveShouldStoreOnlySha256HashWithRefreshTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        RefreshTokenStoreImpl store = new RefreshTokenStoreImpl(template);

        store.save(42L, "session-id", "plain-refresh-token", 604800L);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(operations).set(
                org.mockito.ArgumentMatchers.eq("auth:refresh:42:session-id"),
                valueCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(604800L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)
        );
        assertThat(valueCaptor.getValue())
                .hasSize(64)
                .doesNotContain("plain-refresh-token");
    }
}
