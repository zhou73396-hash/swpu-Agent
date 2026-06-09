package com.swpuagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.swpuagent.mapper")
@SpringBootApplication
public class SwpuAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwpuAgentApplication.class, args);
    }

}
