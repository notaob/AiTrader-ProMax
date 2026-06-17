package com.mp.aitrader.knowledge.service.impl;

import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import com.mp.aitrader.knowledge.domain.AiKnowledgeDoc;
import com.mp.aitrader.knowledge.mapper.AiKnowledgeChunkMapper;
import com.mp.aitrader.knowledge.mapper.AiKnowledgeDocMapper;
import com.mp.aitrader.knowledge.service.AiKnowledgeService;
import com.mp.aitrader.agent.client.LangGraphClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    @Autowired
    private AiKnowledgeDocMapper knowledgeDocMapper;

    @Autowired
    private AiKnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired
    private LangGraphClient langGraphClient;

    @Override
    @Transactional
    public void uploadDocument(AiKnowledgeDoc doc, List<String> chunks) {
        if (doc.getStatus() == null) {
            doc.setStatus("active");
        }
        knowledgeDocMapper.insert(doc);
        Long docId = doc.getId();

        List<Map<String, Object>> chunkData = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunks.get(i));
            chunk.setKeywords(extractKeywords(chunks.get(i)));
            chunk.setCreatedAt(java.time.LocalDateTime.now());
            knowledgeChunkMapper.insert(chunk);

            // 设置 embeddingRef 为 Redis 向量索引的 key
            chunk.setEmbeddingRef("rag:doc:" + chunk.getId());
            knowledgeChunkMapper.updateById(chunk);

            // 构建 Python sync 请求数据
            Map<String, Object> item = new HashMap<>();
            item.put("text", chunk.getChunkText());
            item.put("mysql_chunk_id", chunk.getId());
            item.put("source", doc.getSource() != null ? doc.getSource() : "");
            item.put("chunk_index", chunk.getChunkIndex());
            chunkData.add(item);
        }

        // 调 Python /rag/sync 生成 embedding 并写入向量索引（容错：失败不影响 MySQL）
        try {
            langGraphClient.syncChunksToVectorStore(chunkData, doc.getUserId());
        } catch (Exception e) {
            log.warn("同步向量索引失败，不影响 MySQL 存储: {}", e.getMessage());
        }

        log.info("上传知识文档 {}，分片数: {}，已同步向量索引", doc.getTitle(), chunks.size());
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

    @Override
    @Transactional
    public void deleteDocument(Long docId, Long userId) {
        AiKnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null) {
            throw new RuntimeException("文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人文档");
        }
        knowledgeChunkMapper.deleteByDocId(docId);
        knowledgeDocMapper.deleteById(docId);
        log.info("删除知识文档 id={}, title={}, userId={}", docId, doc.getTitle(), userId);
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
