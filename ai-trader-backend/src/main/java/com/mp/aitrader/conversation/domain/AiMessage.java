package com.mp.aitrader.conversation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiMessage {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private Integer messageIndex;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
