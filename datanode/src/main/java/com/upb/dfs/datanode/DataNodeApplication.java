package com.upb.dfs.datanode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataNodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataNodeApplication.class, args);
    }
}
