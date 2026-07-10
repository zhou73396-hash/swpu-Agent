package com.swpuagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void validAccessTokenShouldSetAndFinallyClearContext() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(claims.get("role", String.class)).thenReturn("user");
        when(jwtUtil.parseToken("access-token")).thenReturn(claims);
        MockHttpServletRequest request = protectedRequest("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            UserContext context = UserContextHolder.get();
            assertThat(context.userId()).isEqualTo(42L);
            assertThat(context.email()).isEqualTo("user@example.com");
        });

        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void refreshTokenShouldNotAccessProtectedEndpoint() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtUtil.parseToken("refresh-token")).thenReturn(claims);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(protectedRequest("refresh-token"), response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_ACCESS_TOKEN_INVALID");
    }

    @Test
    void expiredAccessTokenShouldReturnUnifiedError() throws Exception {
        when(jwtUtil.parseToken("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(protectedRequest("expired-token"), response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_ACCESS_TOKEN_EXPIRED", "timestamp");
    }

    @Test
    void publicEndpointShouldNotRequireToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = {false};

        filter.doFilter(request, response, (req, res) -> invoked[0] = true);

        assertThat(invoked[0]).isTrue();
    }

    @Test
    void contextShouldBeClearedWhenDownstreamThrows() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(claims.get("role", String.class)).thenReturn("user");
        when(jwtUtil.parseToken("access-token")).thenReturn(claims);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(
                        protectedRequest("access-token"),
                        new MockHttpServletResponse(),
                        (req, res) -> {
                            throw new IllegalStateException("downstream failure");
                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(UserContextHolder.get()).isNull();
    }

    private MockHttpServletRequest protectedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/send");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
