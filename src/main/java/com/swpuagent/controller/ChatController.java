package com.swpuagent.controller;

import cn.hutool.json.JSONObject;
import com.swpuagent.agent.AgentClient;
import com.swpuagent.common.auth.AuthErrorCode;
import com.swpuagent.common.auth.AuthException;
import com.swpuagent.dto.request.ChatSendRequest;
import com.swpuagent.security.UserContext;
import com.swpuagent.security.UserContextHolder;
import com.swpuagent.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AgentClient agentClient;

    /**
     * Send message to agent and receive SSE streaming response.
     * JWT protected — userId extracted from token by JwtAuthFilter.
     */
    @PostMapping("/send")
    public SseEmitter send(@Valid @RequestBody ChatSendRequest request) {
        UserContext userContext = currentUser();
        return chatService.sendMessage(request.getQuestion(), userContext.userId(), userContext.email());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JSONObject upload(@RequestParam("file") MultipartFile file) {
        currentUser();
        validateUpload(file);
        return agentClient.uploadFile(file);
    }

    private UserContext currentUser() {
        UserContext userContext = UserContextHolder.get();
        if (userContext == null) {
            throw new AuthException(AuthErrorCode.AUTH_ACCESS_TOKEN_MISSING);
        }
        return userContext;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Uploaded file is empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("Uploaded filename is empty");
        }
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new RuntimeException("Only .docx files are supported");
        }
    }
}
