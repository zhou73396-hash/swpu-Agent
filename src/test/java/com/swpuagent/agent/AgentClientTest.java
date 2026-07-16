package com.swpuagent.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentClientTest {

    private HttpServer server;
    private AgentClient agentClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        agentClient = new AgentClient();
        ReflectionTestUtils.setField(agentClient, "baseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void jsonCallShouldMapNonSuccessfulHttpStatus() {
        server.createContext("/agent/system/chat", exchange -> {
            byte[] body = "{\"message\":\"upstream failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> agentClient.systemChat("hello", "user@example.com"))
                .isInstanceOfSatisfying(AgentClientException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AgentErrorCode.HTTP_ERROR);
                    assertThat(ex.getHttpStatus()).isEqualTo(503);
                });
    }

    @Test
    void jsonCallShouldRejectMalformedResponse() {
        server.createContext("/agent/system/chat", exchange -> {
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> agentClient.systemChat("hello", "user@example.com"))
                .isInstanceOfSatisfying(AgentClientException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AgentErrorCode.PROTOCOL_ERROR));
    }

    @Test
    void cancellationShouldDisconnectBoundStream() {
        AgentStreamCancellation cancellation = new AgentStreamCancellation();
        boolean[] disconnected = {false};
        cancellation.bind(() -> disconnected[0] = true);

        cancellation.cancel();

        assertThat(cancellation.isCancelled()).isTrue();
        assertThat(disconnected[0]).isTrue();
    }
}
