package com.mp.aitrader.conversation.service;

import com.mp.aitrader.conversation.domain.AiConversationSummary;

public interface AiSummaryService {

    boolean shouldCreateSummary(Long conversationId);

    AiConversationSummary getLatestSummary(Long conversationId);

    void generateAndSaveSummary(Long conversationId);
}
