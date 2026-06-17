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
        // 已废弃：记忆提取现在由 Python 端 AI 分类完成，通过 typedMemoryCandidates 返回
        // 保留接口兼容性，不做任何操作
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

    @Override
    public void saveMemoryCandidate(Long userId, String text, String source) {
        // 去重检查：看是否已有高度相似的记忆
        List<AiUserMemory> existing = recallMemories(userId, text, 3);
        for (AiUserMemory m : existing) {
            if (m.getContent().contains(text) || text.contains(m.getContent())) {
                log.info("记忆已存在，跳过: {}", text);
                return;
            }
        }

        // 根据 source 映射 memoryType
        String type;
        switch (source) {
            case "goal":
                type = "goal";
                break;
            case "constraint":
                type = "constraint";
                break;
            case "preference":
            default:
                type = "preference";
                break;
        }

        AiUserMemory memory = new AiUserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(text.length() > 500 ? text.substring(0, 500) : text);
        memory.setImportanceScore(BigDecimal.valueOf(0.7));
        memory.setConfidenceScore(BigDecimal.valueOf(0.6));
        memory.setSource("python_agent");
        memory.setIsActive(1);
        memory.setLastUsedAt(LocalDateTime.now());

        userMemoryMapper.insert(memory);
        log.info("为用户 {} 保存 Python 端记忆候选 [{}]: {}", userId, source, text);
    }

    @Override
    public void saveProfileAnswer(Long userId, String text, String memoryType) {
        AiUserMemory memory = new AiUserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(memoryType);
        memory.setContent(text.length() > 500 ? text.substring(0, 500) : text);
        memory.setImportanceScore(BigDecimal.valueOf(0.9));
        memory.setConfidenceScore(BigDecimal.valueOf(1.0));
        memory.setSource("profile_option");
        memory.setIsActive(1);
        memory.setLastUsedAt(LocalDateTime.now());

        userMemoryMapper.insert(memory);
        log.info("画像引导: 为用户 {} 直接保存画像回答 [{}]: {}", userId, memoryType, text);
    }

    @Override
    public void saveChatMemory(Long userId, String text, String memoryType) {
        // 停用该用户同类型的所有旧记忆（每类只保留最新）
        int deactivated = userMemoryMapper.deactivateByType(userId, memoryType);
        if (deactivated > 0) {
            log.info("用户 {} 停用 {} 条旧 [{}] 类型记忆", userId, deactivated, memoryType);
        }

        // 插入新记忆
        AiUserMemory memory = new AiUserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(memoryType);
        memory.setContent(text.length() > 500 ? text.substring(0, 500) : text);
        memory.setImportanceScore(BigDecimal.valueOf(0.8));
        memory.setConfidenceScore(BigDecimal.valueOf(0.8));
        memory.setSource("ai_classified");
        memory.setIsActive(1);
        memory.setLastUsedAt(LocalDateTime.now());

        userMemoryMapper.insert(memory);
        log.info("用户 {} 保存 AI 分类记忆 [{}]: {}", userId, memoryType, text);
    }

    @Override
    public boolean hasCompleteProfile(Long userId) {
        return getMissingProfileCategories(userId).isEmpty();
    }

    @Override
    public List<String> getMissingProfileCategories(Long userId) {
        List<String> existingTypes = userMemoryMapper.selectDistinctMemoryTypes(userId);
        List<String> missing = new ArrayList<>();

        if (!existingTypes.contains("preference")) {
            missing.add("交易偏好");
        }
        if (!existingTypes.contains("goal")) {
            missing.add("交易目标");
        }
        if (!existingTypes.contains("constraint")) {
            missing.add("风控规则");
        }

        log.info("用户 {} 画像检查: 已有类型={}, 缺失类别={}", userId, existingTypes, missing);
        return missing;
    }

    @Override
    public int clearUserMemories(Long userId) {
        int count = userMemoryMapper.deactivateAllByUserId(userId);
        log.info("用户 {} 清除 {} 条记忆", userId, count);
        return count;
    }
}
