package com.mp.aitrader.conversation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String reply;
    private Long conversationId;
    private Integer remainingChance;  // 策略模式扣减后的剩余次数，供前端实时刷新
}
