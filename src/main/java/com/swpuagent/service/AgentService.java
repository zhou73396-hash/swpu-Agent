package com.swpuagent.service;

import com.swpuagent.agent.AgentClient;
import com.swpuagent.entity.UserInfo;
import com.swpuagent.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent orchestration service — delegates to Python Agent service (FastAPI).
 * <p>
 * The Python /chat endpoint expects user_id to be the user's email (used
 * by its permission middleware: SELECT role FROM user_info WHERE email = ?).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentClient agentClient;
    private final UserInfoMapper userInfoMapper;

    /**
     * Process a user message through the real Python AI Agent pipeline.
     *
     * @param userMessage the user's natural language question
     * @param sessionId   the chat session context (for logging)
     * @param userId      the authenticated user ID
     * @param onEvent     callback for each SSE event (type, content)
     */
    public void processMessage(String userMessage, Long sessionId, Long userId,
                               Consumer<Map.Entry<String, String>> onEvent) {

        log.info("Processing message for session {} user {}: {}", sessionId, userId, truncate(userMessage, 80));

        // Step 1: THINKING — let frontend know we're working
        sendEvent(onEvent, "thinking", "正在分析您的问题…");

        // Step 2: Look up user email — Python /chat expects email as user_id
        String userEmail = lookupEmail(userId);

        // Step 3: Delegate to Python Agent for real AI processing.
        agentClient.chatStream(userMessage, userEmail, onEvent);
    }

    private String lookupEmail(Long userId) {
        try {
            UserInfo user = userInfoMapper.findById(userId);
            if (user != null && user.getEmail() != null) {
                return user.getEmail();
            }
        } catch (Exception e) {
            log.warn("Failed to lookup email for userId={}, falling back to id string", userId, e);
        }
        return String.valueOf(userId);
    }

    private void sendEvent(Consumer<Map.Entry<String, String>> onEvent, String type, String content) {
        onEvent.accept(Map.entry(type, content));
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
