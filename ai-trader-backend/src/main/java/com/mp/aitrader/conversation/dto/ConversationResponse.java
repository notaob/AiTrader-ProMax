package com.mp.aitrader.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 需要被缓存序列化（见 AiConversationCache），因此除 {@code @Builder} 外
 * 还必须有无参构造器供 Jackson 反序列化 —— 仅有 {@code @Builder} 时 Jackson 会失败。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private Long id;
    private Long userId;
    private String title;
    private String sceneType;
    private String status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
