package com.mp.aitrader.conversation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private Integer messageIndex;
    private LocalDateTime createdAt;
}
