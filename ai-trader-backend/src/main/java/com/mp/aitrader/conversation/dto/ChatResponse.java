package com.mp.aitrader.conversation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String reply;
    private Long conversationId;
    private Integer remainingChance;  // 策略模式扣减后的剩余次数，供前端实时刷新
    private List<ProfileOption> profileOptions;  // 画像引导选项
}
