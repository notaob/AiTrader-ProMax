package com.mp.aitrader.context.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiContextLog {
    private Long id;
    private Long conversationId;
    private Long userMessageId;
    private String sceneType;
    private String usedSummaryIds;
    private String usedMemoryIds;
    private String usedKnowledgeIds;
    private BigDecimal retrievalScoreAvg;
    private Integer promptTokenEstimate;
    private String trimAction;
    private String validationStatus;
    private LocalDateTime createdAt;
}
