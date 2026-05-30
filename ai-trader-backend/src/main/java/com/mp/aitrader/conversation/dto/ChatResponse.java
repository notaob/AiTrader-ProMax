package com.mp.aitrader.conversation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String reply;
    private Long conversationId;
}
