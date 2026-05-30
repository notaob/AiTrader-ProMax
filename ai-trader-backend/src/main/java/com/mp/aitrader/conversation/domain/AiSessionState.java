package com.mp.aitrader.conversation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSessionState {
    private Long id;
    private Long conversationId;
    private String currentIntent;
    private String currentMode;
    private String currentStep;
    private String stateJson;
    private LocalDateTime updatedAt;
}
