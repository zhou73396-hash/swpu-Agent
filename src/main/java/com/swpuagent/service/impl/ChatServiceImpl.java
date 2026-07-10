package com.swpuagent.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.swpuagent.agent.AgentClient;
import com.swpuagent.agent.AgentClientException;
import com.swpuagent.agent.AgentErrorCode;
import com.swpuagent.agent.AgentStreamCancellation;
import com.swpuagent.agent.AgentStreamCancelledException;
import com.swpuagent.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AgentClient agentClient;

    @Qualifier("sseExecutor")
    private final TaskExecutor sseExecutor;

    @Override
    public SseEmitter sendMessage(String question, Long userId, String email) {
        log.debug("Dispatching chat request, userId={}", userId);
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        if (question.contains("图表") || lowerQuestion.contains("chart") || question.contains("图")) {
            return handleJsonAgent(question, email, "chart");
        } else if (question.contains("数据分析")
                || question.contains("分析数据")
                || lowerQuestion.contains("analyze")) {
            return handleJsonAgent(question, email, "analyze");
        } else if (question.contains("上传文件成功") || lowerQuestion.contains("file")) {
            return handleStreaming(question, email, "file");
        } else if (question.contains("新闻") || question.contains("热点") || lowerQuestion.contains("news")) {
            return handleStreaming(question, email, "news");
        } else if (question.contains("火车")
                || question.contains("高铁")
                || question.contains("车票")
                || lowerQuestion.contains("train")) {
            return handleStreaming(question, email, "train");
        }
        return handleStreaming(question, email, "sql");
    }

    private SseEmitter handleStreaming(String question, String userId, String agentType) {
        SseEmitter emitter = new SseEmitter(120000L);
        StreamLifecycle lifecycle = new StreamLifecycle(emitter, agentType);
        lifecycle.registerCallbacks();

        try {
            sseExecutor.execute(() -> {
                try {
                    AgentStreamCall call = switch (agentType) {
                        case "file" -> agentClient::fileChat;
                        case "news" -> agentClient::newsChat;
                        case "train" -> agentClient::trainChat;
                        default -> agentClient::sqlChat;
                    };
                    call.execute(question, userId, lifecycle::forwardEvent, lifecycle.cancellation());
                    lifecycle.complete();
                } catch (AgentStreamCancelledException ignored) {
                    log.debug("Agent stream cancelled, type={}", agentType);
                } catch (AgentClientException ex) {
                    lifecycle.fail(ex.getErrorCode(), ex.getMessage());
                } catch (Exception ex) {
                    log.error("Unexpected chat stream failure, type={}", agentType, ex);
                    lifecycle.fail(AgentErrorCode.UNAVAILABLE, "Unexpected Agent stream error");
                }
            });
        } catch (TaskRejectedException ex) {
            lifecycle.fail(AgentErrorCode.UNAVAILABLE, "Agent gateway is busy");
        }
        return emitter;
    }

    private SseEmitter handleJsonAgent(String question, String userId, String eventName) {
        SseEmitter emitter = new SseEmitter(60000L);
        StreamLifecycle lifecycle = new StreamLifecycle(emitter, eventName);
        lifecycle.registerCallbacks();

        try {
            sseExecutor.execute(() -> {
                try {
                    JSONObject result = "chart".equals(eventName)
                            ? agentClient.echartsGenerate(question, userId)
                            : agentClient.analyze(question, userId);
                    if (result.getInt("code", 500) == 200) {
                        lifecycle.send(eventName, result.toString());
                        lifecycle.complete();
                    } else {
                        lifecycle.fail(AgentErrorCode.PROTOCOL_ERROR, "Agent returned a business error");
                    }
                } catch (AgentClientException ex) {
                    lifecycle.fail(ex.getErrorCode(), ex.getMessage());
                } catch (Exception ex) {
                    log.error("Unexpected JSON Agent failure, type={}", eventName, ex);
                    lifecycle.fail(AgentErrorCode.UNAVAILABLE, "Unexpected Agent error");
                }
            });
        } catch (TaskRejectedException ex) {
            lifecycle.fail(AgentErrorCode.UNAVAILABLE, "Agent gateway is busy");
        }
        return emitter;
    }

    @FunctionalInterface
    private interface AgentStreamCall {
        void execute(String question, String userId, java.util.function.Consumer<String> onEvent,
                     AgentStreamCancellation cancellation);
    }

    private static final class StreamLifecycle {

        private final SseEmitter emitter;
        private final String agentType;
        private final AtomicBoolean terminated = new AtomicBoolean(false);
        private final AgentStreamCancellation cancellation = new AgentStreamCancellation();

        private StreamLifecycle(SseEmitter emitter, String agentType) {
            this.emitter = emitter;
            this.agentType = agentType;
        }

        private AgentStreamCancellation cancellation() {
            return cancellation;
        }

        private void registerCallbacks() {
            emitter.onCompletion(() -> terminate("completed"));
            emitter.onTimeout(() -> {
                if (terminate("timeout")) {
                    emitter.complete();
                }
            });
            emitter.onError(error -> terminate("client_error"));
        }

        private void forwardEvent(String eventData) {
            if (terminated.get()) {
                throw new AgentStreamCancelledException();
            }
            JSONObject parsed;
            try {
                parsed = JSONUtil.parseObj(eventData);
            } catch (Exception ex) {
                throw new AgentClientException(AgentErrorCode.PROTOCOL_ERROR,
                        "Agent returned malformed SSE data", ex);
            }

            if (parsed.getBool("error", false)) {
                send("error", eventData);
                complete();
                throw new AgentStreamCancelledException();
            }
            if (parsed.getBool("done", false)) {
                send("done", eventData);
                complete();
                throw new AgentStreamCancelledException();
            }
            if (parsed.containsKey("content")) {
                Object content = parsed.get("content");
                if (content instanceof JSONObject
                        || (content instanceof String text && !text.isEmpty())) {
                    send("text", eventData);
                }
            }
        }

        private void send(String eventName, String data) {
            if (terminated.get()) {
                throw new AgentStreamCancelledException();
            }
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException ex) {
                terminate("disconnected");
                throw new AgentStreamCancelledException();
            }
        }

        private void fail(AgentErrorCode errorCode, String message) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            cancellation.cancel();
            JSONObject error = new JSONObject();
            error.set("code", errorCode.name());
            error.set("message", message);
            try {
                emitter.send(SseEmitter.event().name("error").data(error.toString()));
            } catch (IOException | IllegalStateException ignored) {
                // The client may already have disconnected.
            } finally {
                emitter.complete();
            }
        }

        private void complete() {
            if (terminated.compareAndSet(false, true)) {
                cancellation.cancel();
                emitter.complete();
            }
        }

        private boolean terminate(String reason) {
            if (terminated.compareAndSet(false, true)) {
                log.debug("SSE lifecycle terminated, type={}, reason={}", agentType, reason);
                cancellation.cancel();
                return true;
            }
            return false;
        }
    }
}
