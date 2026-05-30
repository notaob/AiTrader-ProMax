package com.mp.aitrader.conversation.service;

import com.mp.aitrader.conversation.domain.AiConversation;
import com.mp.aitrader.conversation.domain.AiMessage;
import com.mp.aitrader.conversation.domain.AiSessionState;
import com.mp.aitrader.conversation.domain.AiConversationSummary;
import com.mp.aitrader.conversation.dto.*;

import java.util.List;

public interface AiConversationService {

    ConversationResponse createConversation(Long userId, CreateConversationRequest request);

    List<ConversationResponse> getUserConversations(Long userId);

    List<MessageResponse> getConversationMessages(Long conversationId);

    ChatResponse chat(Long conversationId, Long userId, ChatMessageRequest request);

    SessionStateResponse getSessionState(Long conversationId);
}
