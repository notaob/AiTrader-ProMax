package com.mp.aitrader.knowledge.service;

import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import com.mp.aitrader.knowledge.domain.AiKnowledgeDoc;

import java.util.List;

public interface AiKnowledgeService {

    void uploadDocument(AiKnowledgeDoc doc, List<String> chunks);

    List<AiKnowledgeDoc> getAllDocs();

    List<AiKnowledgeChunk> getChunksByDocId(Long docId);

    List<AiKnowledgeChunk> searchKnowledge(String query, int limit);

    void deleteDocument(Long docId, Long userId);
}
