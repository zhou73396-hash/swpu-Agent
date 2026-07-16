package com.swpuagent.service.impl;

import com.swpuagent.agent.AgentClient;
import com.swpuagent.common.auth.AuthErrorCode;
import com.swpuagent.common.auth.AuthException;
import com.swpuagent.dto.response.TokenPairResponse;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.security.JwtUtil;
import com.swpuagent.service.RedisService;
import com.swpuagent.service.UserInfoService;
import com.swpuagent.service.auth.RefreshTokenStore;
import com.swpuagent.utils.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private RedisService redisService;
    @Mock
    private AgentClient agentClient;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserInfoService userInfoService;
    @Mock
    private RefreshTokenStore refreshTokenStore;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                redisService,
                agentClient,
                jwtUtil,
                userInfoService,
                refreshTokenStore
        );
    }

    @Test
    void loginShouldIssueTokenPairAndStoreRefreshSession() {
        UserInfo user = user(42L, "user@example.com", "manager");
        when(redisService.getCode("login:code", user.getEmail())).thenReturn("1234");
        when(userInfoService.getOne(any())).thenReturn(user);
        when(jwtUtil.generateAccessToken(42L, user.getEmail(), "manager")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(eq(42L), anyString())).thenReturn("refresh-token");
        when(jwtUtil.getAccessTokenExpiresInSeconds()).thenReturn(1800L);
        when(jwtUtil.getRefreshTokenExpiresInSeconds()).thenReturn(604800L);

        Result<TokenPairResponse> result = authService.login(user.getEmail(), "1234");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().accessToken()).isEqualTo("access-token");
        assertThat(result.getData().refreshTokenExpiresIn()).isEqualTo(604800L);
        verify(refreshTokenStore).save(eq(42L), anyString(), eq("refresh-token"), eq(604800L));
        verify(redisService).deleteCode("login:code", user.getEmail());
    }

    @Test
    void loginShouldRejectUnknownUserWithoutConsumingCode() {
        when(redisService.getCode("login:code", "missing@example.com")).thenReturn("1234");
        when(userInfoService.getOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login("missing@example.com", "1234"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_USER_NOT_FOUND);
        verify(redisService, never()).deleteCode(any(), any());
    }

    @Test
    void loginShouldRejectInvalidVerificationCode() {
        when(redisService.getCode("login:code", "user@example.com")).thenReturn("1234");

        assertThatThrownBy(() -> authService.login("user@example.com", "9999"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_VERIFICATION_CODE_INVALID);
    }

    @Test
    void refreshShouldAtomicallyRotateToken() {
        Claims claims = refreshClaims(42L, "old-jti");
        UserInfo user = user(42L, "user@example.com", "manager");
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(userInfoService.getById(42L)).thenReturn(user);
        when(jwtUtil.generateAccessToken(42L, user.getEmail(), "manager")).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(eq(42L), anyString())).thenReturn("new-refresh");
        when(jwtUtil.getAccessTokenExpiresInSeconds()).thenReturn(1800L);
        when(jwtUtil.getRefreshTokenExpiresInSeconds()).thenReturn(604800L);
        when(refreshTokenStore.rotate(eq(42L), eq("old-jti"), eq("old-refresh"),
                anyString(), eq("new-refresh"), eq(604800L))).thenReturn(true);

        Result<TokenPairResponse> result = authService.refresh("old-refresh");

        assertThat(result.getData().accessToken()).isEqualTo("new-access");
        assertThat(result.getData().refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void reusedRefreshTokenShouldBeRejected() {
        Claims claims = refreshClaims(42L, "old-jti");
        UserInfo user = user(42L, "user@example.com", "user");
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(userInfoService.getById(42L)).thenReturn(user);
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(eq(42L), anyString())).thenReturn("new-refresh");
        when(jwtUtil.getRefreshTokenExpiresInSeconds()).thenReturn(604800L);

        assertThatThrownBy(() -> authService.refresh("old-refresh"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
    }

    @Test
    void expiredRefreshTokenShouldBeRejected() {
        when(jwtUtil.parseToken("expired-refresh"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> authService.refresh("expired-refresh"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void tamperedRefreshTokenShouldBeRejected() {
        when(jwtUtil.parseToken("tampered-refresh")).thenThrow(new SignatureException("invalid"));

        assertThatThrownBy(() -> authService.refresh("tampered-refresh"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void logoutShouldRevokeOnlySubmittedSession() {
        Claims claims = refreshClaims(42L, "session-a");
        when(jwtUtil.parseToken("refresh-a")).thenReturn(claims);
        when(refreshTokenStore.revoke(42L, "session-a", "refresh-a")).thenReturn(true);

        Result<Void> result = authService.logout("refresh-a");

        assertThat(result.getCode()).isEqualTo(200);
        verify(refreshTokenStore).revoke(42L, "session-a", "refresh-a");
    }

    @Test
    void repeatedLogoutShouldReturnAuthenticationError() {
        Claims claims = refreshClaims(42L, "session-a");
        when(jwtUtil.parseToken("refresh-a")).thenReturn(claims);
        when(refreshTokenStore.revoke(42L, "session-a", "refresh-a")).thenReturn(false);

        assertThatThrownBy(() -> authService.logout("refresh-a"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
    }

    @Test
    void concurrentRefreshShouldAllowOnlyOneSuccess() throws Exception {
        Claims claims = refreshClaims(42L, "old-jti");
        UserInfo user = user(42L, "user@example.com", "user");
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(userInfoService.getById(42L)).thenReturn(user);
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(eq(42L), anyString())).thenReturn("new-refresh");
        when(jwtUtil.getRefreshTokenExpiresInSeconds()).thenReturn(604800L);
        AtomicBoolean consumed = new AtomicBoolean(false);
        CountDownLatch callers = new CountDownLatch(2);
        when(refreshTokenStore.rotate(eq(42L), eq("old-jti"), eq("old-refresh"),
                anyString(), eq("new-refresh"), eq(604800L))).thenAnswer(invocation -> {
                    callers.countDown();
                    callers.await();
                    return consumed.compareAndSet(false, true);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = List.of(
                    executor.submit(() -> refreshSucceeded()),
                    executor.submit(() -> refreshSucceeded())
            );
            long successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void registerShouldAlwaysCreateOrdinaryUser() {
        when(redisService.getCode("register:code", "new@example.com")).thenReturn("1234");
        when(userInfoService.getOne(any())).thenReturn(null);
        when(userInfoService.save(any(UserInfo.class))).thenReturn(true);

        authService.register("new@example.com", "1234", "New User");

        ArgumentCaptor<UserInfo> captor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoService).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("user");
    }

    private boolean refreshSucceeded() {
        try {
            authService.refresh("old-refresh");
            return true;
        } catch (AuthException ex) {
            assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
            return false;
        }
    }

    private Claims refreshClaims(Long userId, String jti) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.getId()).thenReturn(jti);
        return claims;
    }

    private UserInfo user(Long id, String email, String role) {
        UserInfo user = new UserInfo();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
