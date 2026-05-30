package com.mp.aitrader.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiKnowledgeChunk {
    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String chunkText;
    private String keywords;
    private String embeddingRef;
    private LocalDateTime createdAt;
}
