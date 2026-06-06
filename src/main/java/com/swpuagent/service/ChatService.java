package com.swpuagent.service;

import com.swpuagent.common.exception.NotFoundException;
import com.swpuagent.entity.ChatMessage;
import com.swpuagent.entity.ChatSession;
import com.swpuagent.mapper.ChatMessageMapper;
import com.swpuagent.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    /** List all active sessions for a user */
    public List<ChatSession> listSessions(Long userId) {
        return sessionMapper.findByUserId(userId);
    }

    /** Create a new chat session */
    public ChatSession createSession(Long userId, Long dbConnectionId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setDbConnectionId(dbConnectionId);
        session.setTitle(title != null ? title : "New Chat");
        sessionMapper.insert(session);
        log.info("Session created: id={}, userId={}", session.getId(), userId);
        return session;
    }

    /** Get all messages for a session */
    public List<ChatMessage> getMessages(Long sessionId) {
        ChatSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        return messageMapper.findBySessionId(sessionId);
    }

    /** Delete (soft) a session */
    public void deleteSession(Long sessionId) {
        ChatSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        sessionMapper.softDelete(sessionId);
        log.info("Session deleted: id={}", sessionId);
    }

    /** Save a user message */
    @Transactional
    public ChatMessage saveUserMessage(Long sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("USER");
        msg.setContent(content);
        msg.setMessageType("TEXT");
        msg.setTokenCount(content.length() / 4); // rough estimate
        messageMapper.insert(msg);
        sessionMapper.incrementMessageCount(sessionId);
        return msg;
    }

    /** Save an assistant message */
    @Transactional
    public ChatMessage saveAssistantMessage(Long sessionId, String content, String messageType, String metadata) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        msg.setMessageType(messageType);
        msg.setMetadata(metadata);
        msg.setTokenCount(content.length() / 4);
        messageMapper.insert(msg);
        sessionMapper.incrementMessageCount(sessionId);
        return msg;
    }
}
