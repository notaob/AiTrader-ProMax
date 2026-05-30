package com.mp.aitrader.conversation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConversationSummary {
    private Long id;
    private Long conversationId;
    private Integer startMessageIndex;
    private Integer endMessageIndex;
    private String summaryText;
    private String summaryType;
    private LocalDateTime createdAt;
}
