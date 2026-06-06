package com.swpuagent.service;

import com.swpuagent.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent orchestration service — placeholder for LangChain4j integration.
 * Currently simulates the ReAct pattern (think → act → observe → respond)
 * with mock responses. Replace with real LLM calls when integrating LangChain4j.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    /**
     * Process a user message through the Agent pipeline.
     *
     * @param userMessage the user's natural language question
     * @param sessionId   the chat session context
     * @param onEvent     callback for each SSE event (type, content)
     */
    public void processMessage(String userMessage, Long sessionId,
                               Consumer<Map.Entry<String, String>> onEvent) {

        // Step 1: THINKING — agent analyzes the question
        sendEvent(onEvent, "thinking", "正在分析您的问题：" + truncate(userMessage, 50));

        // Step 2: TOOL_CALL — agent might query database
        sendEvent(onEvent, "tool_call", "{\"tool\":\"execute_sql\",\"status\":\"running\"}");

        // Step 3: SQL — agent generates SQL (placeholder)
        String sql = generateMockSql(userMessage);
        sendEvent(onEvent, "sql", sql);

        // Step 4: TOOL_RESULT — query result
        sendEvent(onEvent, "tool_result", "{\"tool\":\"execute_sql\",\"status\":\"success\",\"row_count\":3}");

        // Step 5: TEXT — natural language answer
        sendEvent(onEvent, "text", generateMockAnswer(userMessage));

        // Step 6: CHART — optional chart config
        if (userMessage.contains("图表") || userMessage.contains("chart") || userMessage.contains("柱状图")) {
            sendEvent(onEvent, "chart", generateMockChart());
        }

        // Step 7: DONE
        sendEvent(onEvent, "done", "");
    }

    private void sendEvent(Consumer<Map.Entry<String, String>> onEvent, String type, String content) {
        onEvent.accept(Map.entry(type, content));
        try {
            Thread.sleep(400); // simulate processing delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateMockSql(String question) {
        return "SELECT category, SUM(amount) AS total\n" +
               "FROM sales\n" +
               "WHERE sale_date >= '2026-05-01'\n" +
               "GROUP BY category\n" +
               "ORDER BY total DESC\n" +
               "LIMIT 5";
    }

    private String generateMockAnswer(String question) {
        return "根据查询结果，以下是您的数据分析：\n\n" +
               "1. **电子产品** — ¥158,000\n" +
               "2. **家居用品** — ¥124,500\n" +
               "3. **服装鞋帽** — ¥98,200\n" +
               "4. **食品饮料** — ¥76,800\n" +
               "5. **运动户外** — ¥52,300\n\n" +
               "> 注意：当前为模拟数据。接入 LangChain4j + 外部数据库后将返回真实查询结果。";
    }

    private String generateMockChart() {
        return "{\"chartType\":\"bar\",\"option\":{" +
               "\"title\":{\"text\":\"各品类销售额\"}," +
               "\"xAxis\":{\"type\":\"category\",\"data\":[\"电子\",\"家居\",\"服装\",\"食品\",\"运动\"]}," +
               "\"yAxis\":{\"type\":\"value\",\"name\":\"销售额 (¥)\"}," +
               "\"series\":[{\"type\":\"bar\",\"data\":[158000,124500,98200,76800,52300]}]}}";
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
