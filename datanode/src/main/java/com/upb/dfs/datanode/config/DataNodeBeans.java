package com.upb.dfs.datanode.config;

import com.upb.dfs.common.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataNodeBeans {
    @Bean
    public JwtUtil jwtUtil(DataNodeProperties props) {
        return new JwtUtil(props.getJwtSecret(), 24L * 3600_000L);
    }
}
