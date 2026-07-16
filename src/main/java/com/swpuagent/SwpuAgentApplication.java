package com.swpuagent;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.swpuagent.mapper")
@SpringBootApplication
public class SwpuAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(SwpuAgentApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SwpuAgentApplication.class, args);
        log.info("Application version: {}", context.getEnvironment().getProperty("app.version", "unknown"));
    }

}
