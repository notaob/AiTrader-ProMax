package com.mp.aitrader.conversation.service;

import com.mp.aitrader.conversation.domain.AiSessionState;

import java.util.Map;

public interface AiSessionStateService {

    void initSessionState(Long conversationId);

    AiSessionState getSessionState(Long conversationId);

    void updateSessionState(Long conversationId, String userMessage, String aiReply);

    void updateSessionStateFromPatch(Long conversationId, Map<String, Object> statePatch);
}
