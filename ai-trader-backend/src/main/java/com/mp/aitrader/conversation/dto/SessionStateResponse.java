package com.mp.aitrader.conversation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionStateResponse {
    private Long id;
    private Long conversationId;
    private String currentIntent;
    private String currentMode;
    private String currentStep;
    private String stateJson;
    private LocalDateTime updatedAt;
}
