package com.mp.aitrader.memory.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiUserMemory {
    private Long id;
    private Long userId;
    private String memoryType;
    private String content;
    private BigDecimal importanceScore;
    private BigDecimal confidenceScore;
    private String source;
    private Integer isActive;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
