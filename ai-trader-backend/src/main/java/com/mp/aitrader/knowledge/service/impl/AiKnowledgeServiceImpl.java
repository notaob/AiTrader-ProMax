package com.mp.aitrader.knowledge.service.impl;

import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import com.mp.aitrader.knowledge.domain.AiKnowledgeDoc;
import com.mp.aitrader.knowledge.mapper.AiKnowledgeChunkMapper;
import com.mp.aitrader.knowledge.mapper.AiKnowledgeDocMapper;
import com.mp.aitrader.knowledge.service.AiKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    @Autowired
    private AiKnowledgeDocMapper knowledgeDocMapper;

    @Autowired
    private AiKnowledgeChunkMapper knowledgeChunkMapper;

    @Override
    @Transactional
    public void uploadDocument(AiKnowledgeDoc doc, List<String> chunks) {
        if (doc.getStatus() == null) {
            doc.setStatus("active");
        }
        knowledgeDocMapper.insert(doc);
        Long docId = doc.getId();

        for (int i = 0; i < chunks.size(); i++) {
            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunks.get(i));
            chunk.setKeywords(extractKeywords(chunks.get(i)));
            chunk.setEmbeddingRef("");
            knowledgeChunkMapper.insert(chunk);
        }

        log.info("上传知识文档 {}，分片数: {}", doc.getTitle(), chunks.size());
    }

    @Override
    public List<AiKnowledgeDoc> getAllDocs() {
        return knowledgeDocMapper.selectAll();
    }

    @Override
    public List<AiKnowledgeChunk> getChunksByDocId(Long docId) {
        return knowledgeChunkMapper.selectByDocId(docId);
    }

    @Override
    public List<AiKnowledgeChunk> searchKnowledge(String query, int limit) {
        String lowerQuery = query.toLowerCase();
        List<AiKnowledgeDoc> docs = knowledgeDocMapper.selectAll();
        List<AiKnowledgeChunk> allChunks = new ArrayList<>();

        for (AiKnowledgeDoc doc : docs) {
            List<AiKnowledgeChunk> chunks = knowledgeChunkMapper.selectByDocId(doc.getId());
            if (chunks != null) {
                allChunks.addAll(chunks);
            }
        }

        return allChunks.stream()
                .filter(c -> {
                    boolean match = false;
                    if (c.getChunkText() != null && c.getChunkText().toLowerCase().contains(lowerQuery)) {
                        match = true;
                    }
                    if (c.getKeywords() != null && c.getKeywords().toLowerCase().contains(lowerQuery)) {
                        match = true;
                    }
                    return match;
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            String clean = word.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
            if (clean.length() > 1) {
                sb.append(clean).append(",");
            }
        }
        String result = sb.toString();
        return result.length() > 200 ? result.substring(0, 200) : result;
    }
}
