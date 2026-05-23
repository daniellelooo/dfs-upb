package com.upb.dfs.datanode.security;

import com.upb.dfs.common.util.JwtUtil;
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
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.equals("/health") || p.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String authz = req.getHeader("Authorization");
        if (authz == null || !authz.startsWith("Bearer ")) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"missing token\"}");
            return;
        }
        try {
            jwtUtil.parse(authz.substring(7));
        } catch (Exception ex) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"invalid token\"}");
            return;
        }
        chain.doFilter(req, resp);
    }
}
