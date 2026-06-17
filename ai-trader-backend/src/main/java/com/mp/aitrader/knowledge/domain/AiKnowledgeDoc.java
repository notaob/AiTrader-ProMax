package com.mp.aitrader.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiKnowledgeDoc {
    private Long id;
    private Long userId;
    private String docType;
    private String title;
    private String source;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
