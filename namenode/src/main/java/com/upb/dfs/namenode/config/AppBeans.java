package com.upb.dfs.namenode.config;

import com.upb.dfs.common.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class AppBeans {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtUtil jwtUtil(DfsProperties props) {
        return new JwtUtil(props.getJwtSecret(), props.getJwtExpirationHours() * 3600_000L);
    }
}
