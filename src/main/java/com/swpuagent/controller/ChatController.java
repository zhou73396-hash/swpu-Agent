package com.swpuagent.controller;

import com.swpuagent.dto.request.ChatSendRequest;
import com.swpuagent.dto.request.CreateSessionRequest;
import com.swpuagent.dto.response.ApiResponse;
import com.swpuagent.entity.ChatMessage;
import com.swpuagent.entity.ChatSession;
import com.swpuagent.service.AgentService;
import com.swpuagent.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AgentService agentService;
    private final Executor sseExecutor;

    public ChatController(ChatService chatService,
                          AgentService agentService,
                          @Qualifier("sseExecutor") Executor sseExecutor) {
        this.chatService = chatService;
        this.agentService = agentService;
        this.sseExecutor = sseExecutor;
    }

    /** List sessions — GET /api/chat/sessions */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> listSessions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.success(chatService.listSessions(userId));
    }

    /** Create session — POST /api/chat/sessions */
    @PostMapping("/sessions")
    public ApiResponse<ChatSession> createSession(@RequestBody CreateSessionRequest req,
                                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        ChatSession session = chatService.createSession(userId, req.getDbConnectionId(), req.getTitle());
        return ApiResponse.success(session);
    }

    /** Get messages — GET /api/chat/sessions/{id}/messages */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessage>> getMessages(@PathVariable("id") Long sessionId) {
        return ApiResponse.success(chatService.getMessages(sessionId));
    }

    /** Delete session — DELETE /api/chat/sessions/{id} */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable("id") Long sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResponse.success("会话已删除", null);
    }

    /**
     * Send message to Agent — POST /api/chat/send
     * Returns SSE (Server-Sent Events) stream.
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@Valid @RequestBody ChatSendRequest request,
                                   HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        CompletableFuture.runAsync(() -> {
            try {
                // Save user message
                ChatMessage userMsg = chatService.saveUserMessage(request.getSessionId(), request.getMessage());
                sendSse(emitter, "user_saved", "{\"message_id\":" + userMsg.getId() + "}");

                // Agent pipeline: each event → SSE to frontend
                StringBuilder fullAnswer = new StringBuilder();
                String[] chart = {null};

                agentService.processMessage(request.getMessage(), request.getSessionId(), userId, event -> {
                    try {
                        String eventType = event.getKey();
                        String eventData = event.getValue();

                        sendSse(emitter, eventType, eventData);

                        if ("text".equals(eventType)) {
                            fullAnswer.append(eventData);
                        }
                        if ("chart".equals(eventType)) {
                            chart[0] = eventData;
                        }
                    } catch (IOException e) {
                        log.error("SSE send failed", e);
                    }
                });

                // Save assistant message
                String messageType = (chart[0] != null) ? "CHART" : "TEXT";
                String content = fullAnswer.length() > 0 ? fullAnswer.toString() : "(empty response)";
                chatService.saveAssistantMessage(request.getSessionId(),
                        content, messageType, chart[0]);

                emitter.complete();
            } catch (Exception e) {
                log.error("Agent processing error", e);
                try {
                    sendSse(emitter, "error", e.getMessage());
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, sseExecutor);  // isolated SSE thread pool, not ForkJoinPool

        return emitter;
    }

    private void sendSse(SseEmitter emitter, String eventType, String data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(data, MediaType.APPLICATION_JSON));
    }
}
