package com.mp.aitrader.conversation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConversation {
    private Long id;
    private Long userId;
    private String title;
    private String sceneType;
    private String status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
