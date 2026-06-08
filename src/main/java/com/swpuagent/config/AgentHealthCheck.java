package com.swpuagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Probe the Python Agent service on startup.
 * Logs a warning if unreachable — Java can still serve Auth, Sessions, Viz.
 */
@Slf4j
@Component
public class AgentHealthCheck {

    private final String agentBaseUrl;

    public AgentHealthCheck(@Value("${agent.python.base-url:http://localhost:8000}") String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void probe() {
        String url = agentBaseUrl + "/docs";
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("Python Agent is reachable at {} (HTTP {})", agentBaseUrl, resp.statusCode());
            } else {
                log.warn("Python Agent responded HTTP {} at {} — chat may not work", resp.statusCode(), agentBaseUrl);
            }
        } catch (Exception e) {
            log.warn("Python Agent NOT reachable at {} — chat will fail, but Auth/Sessions/Viz still work. " +
                     "Start it with: cd agent-py && python main.py", agentBaseUrl);
            log.debug("Probe error detail: {}", e.getMessage());
        }
    }
}
