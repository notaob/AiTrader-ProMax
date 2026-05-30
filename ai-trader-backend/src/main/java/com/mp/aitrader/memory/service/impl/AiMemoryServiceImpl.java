package com.mp.aitrader.memory.service.impl;

import com.mp.aitrader.memory.domain.AiMemoryEmbedding;
import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.mapper.AiMemoryEmbeddingMapper;
import com.mp.aitrader.memory.mapper.AiUserMemoryMapper;
import com.mp.aitrader.memory.service.AiMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiMemoryServiceImpl implements AiMemoryService {

    @Autowired
    private AiUserMemoryMapper userMemoryMapper;

    @Autowired
    private AiMemoryEmbeddingMapper memoryEmbeddingMapper;

    @Override
    public void extractMemory(Long userId, String userMessage, String aiResponse) {
        String combined = (userMessage + " " + aiResponse).toLowerCase();

        List<String> extractedContents = new ArrayList<>();

        if (combined.contains("偏好") || combined.contains("喜欢") || combined.contains("习惯")) {
            extractedContents.add("用户偏好: " + userMessage);
        }
        if (combined.contains("策略") || combined.contains("规则") || combined.contains("条件")) {
            extractedContents.add("用户策略: " + userMessage);
        }
        if (combined.contains("目标") || combined.contains("计划") || combined.contains("期望")) {
            extractedContents.add("用户目标: " + userMessage);
        }
        if (combined.contains("风险") || combined.contains("止损") || combined.contains("仓位")) {
            extractedContents.add("风险偏好: " + userMessage);
        }

        for (String content : extractedContents) {
            AiUserMemory memory = new AiUserMemory();
            memory.setUserId(userId);
            memory.setMemoryType("preference");
            memory.setContent(content.length() > 500 ? content.substring(0, 500) : content);
            memory.setImportanceScore(BigDecimal.valueOf(0.7));
            memory.setConfidenceScore(BigDecimal.valueOf(0.6));
            memory.setSource("chat");
            memory.setIsActive(1);
            memory.setLastUsedAt(LocalDateTime.now());

            userMemoryMapper.insert(memory);
            log.info("为用户 {} 提取记忆: {}", userId, content);
        }
    }

    @Override
    public List<AiUserMemory> getUserMemories(Long userId) {
        return userMemoryMapper.selectActiveByUserId(userId);
    }

    @Override
    public void deactivateMemory(Long memoryId) {
        userMemoryMapper.deactivateById(memoryId);
        log.info("停用记忆 {}", memoryId);
    }

    @Override
    public List<AiUserMemory> recallMemories(Long userId, String query, int limit) {
        List<AiUserMemory> activeMemories = userMemoryMapper.selectActiveByUserId(userId);
        if (activeMemories == null || activeMemories.isEmpty()) {
            return new ArrayList<>();
        }

        String lowerQuery = query.toLowerCase();
        return activeMemories.stream()
                .filter(m -> m.getContent() != null && m.getContent().toLowerCase().contains(lowerQuery))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
