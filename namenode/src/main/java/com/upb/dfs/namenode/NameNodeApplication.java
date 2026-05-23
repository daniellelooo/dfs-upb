package com.upb.dfs.namenode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NameNodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(NameNodeApplication.class, args);
    }
}
