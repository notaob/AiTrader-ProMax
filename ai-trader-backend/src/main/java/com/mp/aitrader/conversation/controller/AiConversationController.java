package com.mp.aitrader.conversation.controller;

import com.mp.aitrader.VO.Result;
import com.mp.aitrader.conversation.dto.*;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai/conversations")
public class AiConversationController {

    @Autowired
    private AiConversationService conversationService;

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
