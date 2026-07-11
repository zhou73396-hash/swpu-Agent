package com.swpuagent.controller;

import com.swpuagent.utils.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String appVersion;

    public HealthController(@Value("${app.version}") String appVersion) {
        this.appVersion = appVersion;
    }

    @GetMapping
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "service", "swpu-agent-gateway",
                "version", appVersion
        ));
    }
}
