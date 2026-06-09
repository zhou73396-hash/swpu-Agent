package com.swpuagent.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentClient {

    @Value("${agent.base-url:http://192.168.158.56:8000}")
    private String baseUrl;

    public JSONObject systemChat(String message,String email) {
        String url = baseUrl + "/agent/system/chat";
        JSONObject body = new JSONObject();
        body.set("message", message);
        body.set("user_id",email);
        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(30000)
                .execute()) {
            String resultBody = response.body();
            log.info("SystemAgent response: {}", resultBody);
            return JSONUtil.parseObj(resultBody);
        } catch (Exception e) {
            log.error("SystemAgent call failed: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.set("code", "500");
            error.set("msg", "System agent unavailable: " + e.getMessage());
            return error;
        }
    }

    public void sqlChat(String question, String userId, Consumer<String> onEvent) {
        streamChat("/agent/sql/chat", question, userId, onEvent);
    }

    public JSONObject echartsGenerate(String question, String userId) {
        String url = baseUrl + "/agent/echarts/generate";
        JSONObject body = new JSONObject();
        body.set("question", question);
        body.set("user_id", userId);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(60000)
                .execute()) {
            String resultBody = response.body();
            log.info("EchartsAgent response: {}", resultBody);
            return JSONUtil.parseObj(resultBody);
        } catch (Exception e) {
            log.error("EchartsAgent call failed: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.set("code", 500);
            error.set("msg", "ECharts agent unavailable: " + e.getMessage());
            return error;
        }
    }

    public JSONObject analyze(String question, String userId) {
        String url = baseUrl + "/agent/analyze";
        JSONObject body = new JSONObject();
        body.set("question", question);
        body.set("user_id", userId);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(60000)
                .execute()) {
            String resultBody = response.body();
            log.info("AnalyzeAgent response: {}", resultBody);
            return JSONUtil.parseObj(resultBody);
        } catch (Exception e) {
            log.error("AnalyzeAgent call failed: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.set("code", 500);
            error.set("msg", "Analyze agent unavailable: " + e.getMessage());
            return error;
        }
    }

    public JSONObject uploadFile(MultipartFile file) {
        String url = baseUrl + "/upload";
        String filename = sanitizeFilename(file.getOriginalFilename());

        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            RestTemplate restTemplate = new RestTemplate(createRequestFactory());
            String responseBody = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            log.info("File upload response: {}", responseBody);
            return JSONUtil.parseObj(responseBody);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("File upload failed with status {}: {}", e.getStatusCode(), responseBody);
            return parseUploadError(responseBody, e.getMessage());
        } catch (Exception e) {
            log.error("File upload failed: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.set("code", 500);
            error.set("msg", "File upload unavailable: " + e.getMessage());
            return error;
        }
    }

    public void fileChat(String question, String userId, Consumer<String> onEvent) {
        streamChat("/agent/file/chat", question, userId, onEvent);
    }

    public void newsChat(String question, String userId, Consumer<String> onEvent) {
        streamChat("/agent/news/chat", question, userId, onEvent);
    }

    public void trainChat(String question, String userId, Consumer<String> onEvent) {
        streamChat("/agent/train/chat", question, userId, onEvent);
    }

    private void streamChat(String path, String question, String userId, Consumer<String> onEvent) {
        String url = baseUrl + path;
        JSONObject body = new JSONObject();
        body.set("question", question);
        body.set("user_id", userId);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(120000);

            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(input);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (StrUtil.isNotBlank(data)) {
                            onEvent.accept(data);
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            log.error("SSE stream {} failed: {}", path, e.getMessage());
            onEvent.accept("{\"content\":{\"text\":\"Agent service error: " +
                    e.getMessage() + "\",\"done\":false},\"done\":false}");
            onEvent.accept("{\"content\":\"\",\"done\":true}");
        }
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        return factory;
    }

    private JSONObject parseUploadError(String responseBody, String fallbackMessage) {
        if (StringUtils.hasText(responseBody) && JSONUtil.isTypeJSON(responseBody)) {
            return JSONUtil.parseObj(responseBody);
        }
        JSONObject error = new JSONObject();
        error.set("code", 500);
        error.set("msg", Objects.requireNonNullElse(fallbackMessage, "File upload failed"));
        return error;
    }

    private String sanitizeFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "upload.docx";
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        return StringUtils.cleanPath(filename);
    }
}
