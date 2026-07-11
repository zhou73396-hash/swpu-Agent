package com.swpuagent.service.impl;

import com.swpuagent.agent.AgentClient;
import com.swpuagent.agent.AgentStreamCancellation;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    @Test
    void defaultQuestionShouldRouteToSqlAgentAndFinishOnDoneEvent() {
        AgentClient agentClient = mock(AgentClient.class);
        doAnswer(invocation -> {
            Consumer<String> callback = invocation.getArgument(2);
            callback.accept("{\"content\":\"finished\",\"done\":true}");
            return null;
        }).when(agentClient).sqlChat(eq("查询销售额"), eq("user@example.com"), any(),
                any(AgentStreamCancellation.class));
        TaskScheduler timeoutScheduler = mock(TaskScheduler.class);
        when(timeoutScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
                .thenReturn(mock(java.util.concurrent.ScheduledFuture.class));
        ChatServiceImpl chatService = new ChatServiceImpl(
                agentClient,
                new SyncTaskExecutor(),
                timeoutScheduler
        );

        SseEmitter emitter = chatService.sendMessage("查询销售额", 42L, "user@example.com");

        assertThat(emitter).isNotNull();
        verify(agentClient).sqlChat(eq("查询销售额"), eq("user@example.com"), any(),
                any(AgentStreamCancellation.class));
    }
}
