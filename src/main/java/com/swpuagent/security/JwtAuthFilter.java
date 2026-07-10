package com.swpuagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swpuagent.common.auth.ApiErrorResponse;
import com.swpuagent.common.auth.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final String[] PROTECTED_PATHS = {
            "/api/chat/",
            "/api/db/",
            "/api/viz/",
            "/api/user/"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!isProtected(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, AuthErrorCode.AUTH_ACCESS_TOKEN_MISSING);
            return;
        }

        String token = authHeader.substring(7);
        UserContext userContext;
        try {
            Claims claims = jwtUtil.parseToken(token);
            if (!"access".equals(claims.get("type", String.class))) {
                writeError(response, AuthErrorCode.AUTH_ACCESS_TOKEN_INVALID);
                return;
            }
            userContext = new UserContext(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class)
            );
            if (userContext.email() == null || userContext.role() == null) {
                writeError(response, AuthErrorCode.AUTH_ACCESS_TOKEN_INVALID);
                return;
            }
        } catch (ExpiredJwtException ex) {
            writeError(response, AuthErrorCode.AUTH_ACCESS_TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            writeError(response, AuthErrorCode.AUTH_ACCESS_TOKEN_INVALID);
            return;
        }

        UserContextHolder.set(userContext);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isProtected(String path) {
        for (String protectedPath : PROTECTED_PATHS) {
            if (path.startsWith(protectedPath)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, AuthErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(errorCode, errorCode.getMessage()));
    }
}
