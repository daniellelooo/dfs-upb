package com.upb.dfs.namenode.config;

import com.upb.dfs.namenode.security.AuthHandlerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthHandlerInterceptor authHandlerInterceptor;

    public WebConfig(AuthHandlerInterceptor authHandlerInterceptor) {
        this.authHandlerInterceptor = authHandlerInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authHandlerInterceptor);
    }
}
