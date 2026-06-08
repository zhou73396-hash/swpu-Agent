package com.swpuagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * HTTP client for the Python Agent service (FastAPI on port 8000).
 * <p>
 * The Python service hosts real LangChain agents:
 * <ul>
 *   <li>SqlQuestionAgent — SQL query answering (SSE streaming)</li>
 *   <li>EchartsAgent — chart generation</li>
 *   <li>AnalyzeAgent — data analysis</li>
 *   <li>FileAnalyzeAgent — file analysis (SSE streaming)</li>
 *   <li>NewsAgent — news retrieval (SSE streaming)</li>
 * </ul>
 * Note: Auth (send_code / send_register_code) is handled by Java directly,
 * no LLM cost. Only /chat goes to Python.
 */
@Slf4j
@Component
public class AgentClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AgentClient(@Value("${agent.python.base-url:http://localhost:8000}") String baseUrl,
                       ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
        log.info("AgentClient initialized with base URL: {}", baseUrl);
    }

    // ==================== Chat (SSE relay) ====================

    /**
     * Call the Python /chat endpoint and relay the response through a callback.
     * <p>
     * Python returns one of:
     * <ul>
     *   <li>SSE stream (text/event-stream) for SqlQuestionAgent / FileAnalyzeAgent / NewsAgent.
     *       Each line: {@code data:{"content":{...},"done":false}}</li>
     *   <li>JSON (application/json) for EchartsAgent / AnalyzeAgent.
     *       Body: {@code {"code":200,"data":{...}}}</li>
     * </ul>
     *
     * @param question user's natural language question
     * @param userId   user ID (passed as query param to Python)
     * @param onEvent  callback for each SSE event (eventType, data)
     */
    public void chatStream(String question, String userId,
                           Consumer<Map.Entry<String, String>> onEvent) {
        log.info("Calling Python /chat?question={}&user_id={}", truncate(question, 80), userId);
        try {
            restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/chat")
                            .queryParam("question", question)
                            .queryParam("user_id", userId)
                            .build())
                    .exchange((request, response) -> {
                        MediaType contentType = response.getHeaders().getContentType();
                        log.debug("Python /chat response content-type: {}", contentType);

                        if (contentType != null && contentType.includes(MediaType.TEXT_EVENT_STREAM)) {
                            handleSseStream(response.getBody(), onEvent);
                        } else {
                            handleJsonResponse(response.getBody(), onEvent);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to call Python /chat", e);
            onEvent.accept(Map.entry("error", "Agent service unavailable: " + e.getMessage()));
            onEvent.accept(Map.entry("done", ""));
        }
    }

    /**
     * Handle SSE (text/event-stream) response from Python agent.
     * Reads lines, parses "data:..." lines, and relays content to onEvent.
     */
    private void handleSseStream(java.io.InputStream body,
                                 Consumer<Map.Entry<String, String>> onEvent) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring(5).trim();
                if (json.isEmpty() || "[DONE]".equals(json)) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sseData = objectMapper.readValue(json, Map.class);
                    relaySseData(sseData, onEvent);
                } catch (JsonProcessingException e) {
                    log.debug("Non-JSON SSE data: {}", json);
                    onEvent.accept(Map.entry("text", json));
                }
            }
        } catch (Exception e) {
            log.error("Error reading SSE stream from Python agent", e);
            onEvent.accept(Map.entry("error", "Stream read error: " + e.getMessage()));
            onEvent.accept(Map.entry("done", ""));
        }
    }

    /**
     * Handle plain JSON response from Python agent (EchartsAgent, AnalyzeAgent).
     */
    @SuppressWarnings("unchecked")
    private void handleJsonResponse(java.io.InputStream body,
                                    Consumer<Map.Entry<String, String>> onEvent) {
        try {
            byte[] bytes = body.readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            log.debug("Python /chat JSON response: {}", truncate(json, 200));
            try {
                Map<String, Object> response = objectMapper.readValue(json, Map.class);
                Object code = response.get("code");
                Object data = response.get("data");

                if (data instanceof Map) {
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    // Check if it's an ECharts response
                    if (dataMap.containsKey("option") || dataMap.containsKey("chart_type")) {
                        onEvent.accept(Map.entry("chart", objectMapper.writeValueAsString(dataMap)));
                    } else {
                        onEvent.accept(Map.entry("text", objectMapper.writeValueAsString(dataMap)));
                    }
                } else if (data instanceof String) {
                    onEvent.accept(Map.entry("text", (String) data));
                } else {
                    onEvent.accept(Map.entry("text", json));
                }
                onEvent.accept(Map.entry("done", ""));
            } catch (Exception e) {
                // If not JSON, pass as raw text
                onEvent.accept(Map.entry("text", json));
                onEvent.accept(Map.entry("done", ""));
            }
        } catch (Exception e) {
            log.error("Error reading JSON response from Python agent", e);
            onEvent.accept(Map.entry("error", "Read error: " + e.getMessage()));
            onEvent.accept(Map.entry("done", ""));
        }
    }

    /**
     * Relay a single SSE data object from Python to Java event format.
     * <p>
     * Python format: {"content": {"text": "...", "done": false}, "done": false}
     * The inner "content" object may contain {"text": "...", "done": false} or {"done": true}.
     */
    @SuppressWarnings("unchecked")
    private void relaySseData(Map<String, Object> sseData,
                              Consumer<Map.Entry<String, String>> onEvent) {
        Object contentObj = sseData.get("content");
        boolean outerDone = Boolean.TRUE.equals(sseData.get("done"));

        if (contentObj instanceof Map) {
            Map<String, Object> content = (Map<String, Object>) contentObj;
            boolean innerDone = Boolean.TRUE.equals(content.get("done"));
            Object text = content.get("text");

            if (text instanceof String && !((String) text).isEmpty()) {
                onEvent.accept(Map.entry("text", (String) text));
            }

            if (innerDone || outerDone) {
                onEvent.accept(Map.entry("done", ""));
            }
        } else if (contentObj instanceof String && !((String) contentObj).isEmpty()) {
            onEvent.accept(Map.entry("text", (String) contentObj));
        }

        if (outerDone && !(contentObj instanceof Map)) {
            onEvent.accept(Map.entry("done", ""));
        }
    }

    // ==================== File Upload ====================

    /**
     * Upload a file to the Python agent for analysis.
     *
     * @param fileBytes file content
     * @param filename  original filename
     * @return {"code": 200, "file_name": "...", "msg": "上传成功"}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadFile(byte[] fileBytes, String filename) {
        log.info("Uploading file to Python /upload: {}", filename);
        try {
            // Python /upload expects multipart/form-data
            String body = restClient.post()
                    .uri("/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(createMultipartBody(fileBytes, filename))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            log.error("Failed to upload file to Python", e);
            return Map.of("code", 500, "msg", "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * Build a simple multipart form-data body.
     * Uses org.springframework.util.LinkedMultiValueMap for RestClient compatibility.
     */
    private org.springframework.util.MultiValueMap<String, Object> createMultipartBody(
            byte[] fileBytes, String filename) {
        org.springframework.util.LinkedMultiValueMap<String, Object> parts =
                new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
        parts.add("file", resource);
        return parts;
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
