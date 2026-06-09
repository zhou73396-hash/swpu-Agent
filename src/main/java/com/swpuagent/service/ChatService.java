package com.swpuagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    SseEmitter sendMessage(String question, String userId);
}
