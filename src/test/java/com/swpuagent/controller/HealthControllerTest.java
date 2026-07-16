package com.swpuagent.controller;

import com.swpuagent.utils.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void healthShouldExposeGatewayStatusWithoutExternalDependencies() {
        Result<Map<String, String>> result = new HealthController("auth-v2").health();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData())
                .containsEntry("status", "UP")
                .containsEntry("service", "swpu-agent-gateway")
                .containsEntry("version", "auth-v2");
    }
}
