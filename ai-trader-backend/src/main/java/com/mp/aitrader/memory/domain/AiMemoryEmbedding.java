package com.mp.aitrader.memory.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiMemoryEmbedding {
    private Long id;
    private Long memoryId;
    private String embeddingRef;
    private String embeddingModel;
    private LocalDateTime createdAt;
}
