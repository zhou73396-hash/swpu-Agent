package com.swpuagent.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.swpuagent.agent.AgentClient;
import com.swpuagent.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AgentClient agentClient;

    @Override
    public SseEmitter sendMessage(String question, String userId) {
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        if (question.contains("\u56fe\u8868") || lowerQuestion.contains("chart") || question.contains("\u56fe")) {
            return handleEcharts(question, userId);
        } else if (question.contains("\u6570\u636e\u5206\u6790")
                || question.contains("\u5206\u6790\u6570\u636e")
                || lowerQuestion.contains("analyze")) {
            return handleAnalyze(question, userId);
        } else if (question.contains("\u4e0a\u4f20\u6587\u4ef6\u6210\u529f") || lowerQuestion.contains("file")) {
            return handleStreaming(question, userId, "file");
        } else if (question.contains("\u65b0\u95fb") || question.contains("\u70ed\u70b9") || lowerQuestion.contains("news")) {
            return handleNewsStreaming(question, userId);
        } else if (question.contains("\u706b\u8f66")
                || question.contains("\u9ad8\u94c1")
                || question.contains("\u8f66\u7968")
                || lowerQuestion.contains("train")
                || lowerQuestion.contains("train ticket")) {
            return handleTrainStreaming(question, userId);
        }
        return handleSqlStreaming(question, userId);
    }

    private SseEmitter handleSqlStreaming(String question, String userId) {
        return createStreamEmitter(onEvent -> agentClient.sqlChat(question, userId, onEvent));
    }

    private SseEmitter handleNewsStreaming(String question, String userId) {
        return createStreamEmitter(onEvent -> agentClient.newsChat(question, userId, onEvent));
    }

    private SseEmitter handleTrainStreaming(String question, String userId) {
        return createStreamEmitter(onEvent -> agentClient.trainChat(question, userId, onEvent));
    }

    private SseEmitter handleStreaming(String question, String userId, String agentType) {
        return createStreamEmitter(onEvent -> {
            if ("file".equals(agentType)) {
                agentClient.fileChat(question, userId, onEvent);
            }
        });
    }

    private SseEmitter createStreamEmitter(AgentStreamCall call) {
        SseEmitter emitter = new SseEmitter(120000L);

        new Thread(() -> {
            try {
                call.execute(eventData -> {
                    try {
                        if (eventData == null || eventData.isEmpty()) {
                            return;
                        }
                        JSONObject parsed = JSONUtil.parseObj(eventData);

                        if (parsed.getBool("done", false)) {
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(eventData));
                            emitter.complete();
                        } else if (parsed.containsKey("content")) {
                            Object content = parsed.get("content");
                            if (content instanceof JSONObject) {
                                emitter.send(SseEmitter.event()
                                        .name("text")
                                        .data(eventData));
                            } else if (content instanceof String && !((String) content).isEmpty()) {
                                emitter.send(SseEmitter.event()
                                        .name("text")
                                        .data(eventData));
                            }
                        }
                    } catch (IOException e) {
                        log.error("SSE send error: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                });

                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                log.error("Chat streaming error: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"Service error: " + e.getMessage() + "\"}"));
                } catch (IOException ex) {
                    log.error("SSE error send failed: {}", ex.getMessage());
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private SseEmitter handleEcharts(String question, String userId) {
        SseEmitter emitter = new SseEmitter(60000L);

        new Thread(() -> {
            try {
                JSONObject result = agentClient.echartsGenerate(question, userId);
                if (result.getInt("code", 500) == 200) {
                    emitter.send(SseEmitter.event()
                            .name("chart")
                            .data(result.toString()));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(result.toString()));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("ECharts error: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private SseEmitter handleAnalyze(String question, String userId) {
        SseEmitter emitter = new SseEmitter(60000L);

        new Thread(() -> {
            try {
                JSONObject result = agentClient.analyze(question, userId);
                if (result.getInt("code", 500) == 200) {
                    emitter.send(SseEmitter.event()
                            .name("analyze")
                            .data(result.toString()));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(result.toString()));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Analyze error: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @FunctionalInterface
    private interface AgentStreamCall {
        void execute(java.util.function.Consumer<String> onEvent);
    }
}
