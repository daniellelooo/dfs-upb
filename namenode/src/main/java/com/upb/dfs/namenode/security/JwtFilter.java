package com.upb.dfs.namenode.security;

import com.upb.dfs.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String authz = request.getHeader("Authorization");
            if (authz != null && authz.startsWith("Bearer ")) {
                String token = authz.substring(7);
                try {
                    Claims claims = jwtUtil.parse(token);
                    Long uid = claims.get("uid", Number.class) != null
                            ? claims.get("uid", Number.class).longValue() : null;
                    AuthContext.set(new AuthContext.Principal(uid, claims.getSubject()));
                } catch (Exception ignored) { /* invalid token => unauthenticated */ }
            }
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/auth/")
                || p.startsWith("/v3/api-docs")
                || p.startsWith("/swagger-ui")
                || p.startsWith("/actuator/health")
                || p.equals("/health")
                || p.startsWith("/h2-console");
    }
}
