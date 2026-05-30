package com.mp.aitrader.conversation.dto;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private String message;
    private String mode; // "chat" 或 "strategy"
}
