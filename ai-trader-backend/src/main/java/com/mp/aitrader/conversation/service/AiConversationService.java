package com.mp.aitrader.conversation.service;

import com.mp.aitrader.conversation.domain.AiConversation;
import com.mp.aitrader.conversation.domain.AiMessage;
import com.mp.aitrader.conversation.domain.AiSessionState;
import com.mp.aitrader.conversation.domain.AiConversationSummary;
import com.mp.aitrader.conversation.dto.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiConversationService {

    ConversationResponse createConversation(Long userId, CreateConversationRequest request);

    List<ConversationResponse> getUserConversations(Long userId);

    List<MessageResponse> getConversationMessages(Long conversationId);

    ChatResponse chat(Long conversationId, Long userId, ChatMessageRequest request);

    /**
     * 流式对话编排（应在独立线程执行，避免占用 Servlet 线程）：
     * - chat 模式：真流式转发 Python SSE（token/tool 帧），结束后落库 + 收尾 + done 帧
     * - strategy 模式 / 画像引导等本地应答场景：委托同步 chat()，整包以 done 帧返回（无 token）
     * - Python 流异常：已发帧则 error 帧收尾，未发帧自动降级同步非流式
     */
    void runChatStream(Long conversationId, Long userId, ChatMessageRequest request, SseEmitter emitter);

    SessionStateResponse getSessionState(Long conversationId);
}
