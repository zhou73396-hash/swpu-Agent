package com.swpuagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret-key=test-only-jwt-secret-key-with-at-least-32-bytes")
class SwpuAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
