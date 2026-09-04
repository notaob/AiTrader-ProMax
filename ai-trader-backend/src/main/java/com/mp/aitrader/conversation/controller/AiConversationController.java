package com.mp.aitrader.conversation.controller;

import com.mp.aitrader.VO.Result;
import com.mp.aitrader.conversation.dto.*;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.context.BaseContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/ai/conversations")
public class AiConversationController {

    @Autowired
    private AiConversationService conversationService;

    /** SSE 流式对话执行线程池（编排含阻塞读 Python SSE，必须脱离 Servlet 线程）。 */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-stream-worker");
        t.setDaemon(true);
        return t;
    });

    @PostMapping("/{id}/chat/stream")
    public SseEmitter chatStream(@PathVariable("id") Long conversationId,
                                 @RequestBody ChatMessageRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 在会话 {} 发起流式对话", userId, conversationId);
        // 300s：覆盖 Python 端思考(≈6s TTFT) + 多轮工具 + 收尾落库；超时后由 onTimeout 自动完成
        SseEmitter emitter = new SseEmitter(300_000L);
        streamExecutor.execute(() -> conversationService.runChatStream(conversationId, userId, request, emitter));
        return emitter;
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdownNow();
    }

    @PostMapping
    public Result<ConversationResponse> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 创建新会话", userId);
        ConversationResponse response = conversationService.createConversation(userId, request);
        return Result.success(response);
    }

    @GetMapping
    public Result<List<ConversationResponse>> getUserConversations() {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 获取会话列表", userId);
        List<ConversationResponse> conversations = conversationService.getUserConversations(userId);
        return Result.success(conversations);
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageResponse>> getConversationMessages(@PathVariable("id") Long conversationId) {
        log.info("获取会话 {} 的消息列表", conversationId);
        List<MessageResponse> messages = conversationService.getConversationMessages(conversationId);
        return Result.success(messages);
    }

    @PostMapping("/{id}/chat")
    public Result<ChatResponse> chat(@PathVariable("id") Long conversationId, @RequestBody ChatMessageRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 在会话 {} 中发送消息", userId, conversationId);
        ChatResponse response = conversationService.chat(conversationId, userId, request);
        return Result.success(response);
    }

    @GetMapping("/{id}/state")
    public Result<SessionStateResponse> getSessionState(@PathVariable("id") Long conversationId) {
        log.info("获取会话 {} 的状态", conversationId);
        SessionStateResponse state = conversationService.getSessionState(conversationId);
        return Result.success(state);
    }
}
