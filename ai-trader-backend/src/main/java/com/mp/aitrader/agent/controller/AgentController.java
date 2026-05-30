package com.mp.aitrader.agent.controller;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.agent.dto.ReActResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Agent API 控制器
 * 作为 API 网关，调用 Python LangGraph 服务
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final LangGraphClient langGraphClient;

    /**
     * 使用 ReAct 模式与 Agent 对话（调用 Python LangGraph 服务）
     */
    @PostMapping("/chat-react")
    public ResponseEntity<ReActResponse> chatWithReAct(@RequestBody ChatRequest request) {
        log.info("收到 ReAct 模式请求: {}", request.getMessage());

        try {
            ReActResponse response = langGraphClient.chatWithReAct(
                    request.getMessage(),
                    request.getUserId(),
                    request.getSessionId(),
                    null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("ReAct 执行异常", e);
            return ResponseEntity.internalServerError().body(
                    ReActResponse.builder()
                            .success(false)
                            .error("执行失败: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * 请求类
     */
    @Data
    public static class ChatRequest {
        private String message;
        private String userId;
        private String sessionId;
    }
}
