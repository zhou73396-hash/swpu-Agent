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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentClient {

    @Value("${agent.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${agent.connect-timeout-ms:10000}")
    private int connectTimeoutMs = 10000;

    @Value("${agent.read-timeout-ms:120000}")
    private int readTimeoutMs = 120000;

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
            return parseJsonResponse(response, "SystemAgent");
        } catch (Exception e) {
            throw mapException("SystemAgent", e);
        }
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
            return parseJsonResponse(response, "EchartsAgent");
        } catch (Exception e) {
            throw mapException("EchartsAgent", e);
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
            return parseJsonResponse(response, "AnalyzeAgent");
        } catch (Exception e) {
            throw mapException("AnalyzeAgent", e);
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
            if (!StringUtils.hasText(responseBody) || !JSONUtil.isTypeJSON(responseBody)) {
                throw new AgentClientException(AgentErrorCode.PROTOCOL_ERROR,
                        "File upload returned invalid JSON", (Integer) null);
            }
            log.debug("File upload call succeeded");
            return JSONUtil.parseObj(responseBody);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            throw new AgentClientException(AgentErrorCode.HTTP_ERROR,
                    "File upload returned HTTP " + e.getStatusCode().value() + formatBodySuffix(responseBody),
                    e.getStatusCode().value());
        } catch (Exception e) {
            throw mapException("File upload", e);
        }
    }

    public void sqlChat(String question, String userId, Consumer<String> onEvent,
                        AgentStreamCancellation cancellation) {
        streamChat("/agent/sql/chat", question, userId, onEvent, cancellation);
    }

    public void fileChat(String question, String userId, Consumer<String> onEvent,
                         AgentStreamCancellation cancellation) {
        streamChat("/agent/file/chat", question, userId, onEvent, cancellation);
    }

    public void newsChat(String question, String userId, Consumer<String> onEvent,
                         AgentStreamCancellation cancellation) {
        streamChat("/agent/news/chat", question, userId, onEvent, cancellation);
    }

    public void trainChat(String question, String userId, Consumer<String> onEvent,
                          AgentStreamCancellation cancellation) {
        streamChat("/agent/train/chat", question, userId, onEvent, cancellation);
    }

    private void streamChat(String path, String question, String userId, Consumer<String> onEvent,
                            AgentStreamCancellation cancellation) {
        String url = baseUrl + path;
        JSONObject body = new JSONObject();
        body.set("question", question);
        body.set("user_id", userId);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            HttpURLConnection connection = conn;
            cancellation.bind(connection::disconnect);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);

            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(input);

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                String errorBody = readBody(conn.getErrorStream());
                throw new AgentClientException(AgentErrorCode.HTTP_ERROR,
                        "Agent returned HTTP " + status + formatBodySuffix(errorBody), status);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancellation.isCancelled()) {
                        throw new AgentStreamCancelledException();
                    }
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (StrUtil.isNotBlank(data)) {
                            onEvent.accept(data);
                        }
                    }
                }
            }
            if (cancellation.isCancelled()) {
                throw new AgentStreamCancelledException();
            }
        } catch (AgentStreamCancelledException e) {
            throw e;
        } catch (Exception e) {
            if (cancellation.isCancelled()) {
                throw new AgentStreamCancelledException();
            }
            throw mapException(path, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }

    private JSONObject parseJsonResponse(HttpResponse response, String agentName) {
        int status = response.getStatus();
        String responseBody = response.body();
        if (status < 200 || status >= 300) {
            throw new AgentClientException(AgentErrorCode.HTTP_ERROR,
                    agentName + " returned HTTP " + status + formatBodySuffix(responseBody), status);
        }
        if (!StringUtils.hasText(responseBody) || !JSONUtil.isTypeJSON(responseBody)) {
            throw new AgentClientException(AgentErrorCode.PROTOCOL_ERROR,
                    agentName + " returned invalid JSON", status);
        }
        log.debug("{} call succeeded with status {}", agentName, status);
        return JSONUtil.parseObj(responseBody);
    }

    private AgentClientException mapException(String agentName, Exception exception) {
        if (exception instanceof AgentClientException agentClientException) {
            return agentClientException;
        }
        if (exception instanceof SocketTimeoutException) {
            return new AgentClientException(AgentErrorCode.READ_TIMEOUT,
                    agentName + " timed out", exception);
        }
        String message = exception.getMessage();
        if (message != null && message.toLowerCase().contains("timed out")) {
            return new AgentClientException(AgentErrorCode.READ_TIMEOUT,
                    agentName + " timed out", exception);
        }
        return new AgentClientException(AgentErrorCode.UNAVAILABLE,
                agentName + " is unavailable", exception);
    }

    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String formatBodySuffix(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return ": " + compact.substring(0, Math.min(compact.length(), 300));
    }

    private String sanitizeFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "upload.docx";
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        return StringUtils.cleanPath(filename);
    }
}
