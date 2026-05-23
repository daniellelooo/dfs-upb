package com.upb.dfs.namenode.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String p = request.getRequestURI();
        if (p.startsWith("/auth/") || p.startsWith("/v3/api-docs") || p.startsWith("/swagger-ui")
                || p.equals("/health") || p.startsWith("/actuator/") || p.startsWith("/h2-console")
                || p.equals("/error")) {
            return true;
        }
        if (AuthContext.get() == null) {
            response.setStatus(401);
            try { response.getWriter().write("{\"error\":\"unauthorized\"}"); } catch (Exception ignored) {}
            return false;
        }
        return true;
    }
}
