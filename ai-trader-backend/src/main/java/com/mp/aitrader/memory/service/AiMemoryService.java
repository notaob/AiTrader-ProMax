package com.mp.aitrader.memory.service;

import com.mp.aitrader.memory.domain.AiUserMemory;

import java.util.List;

public interface AiMemoryService {

    void extractMemory(Long userId, String userMessage, String aiResponse);

    List<AiUserMemory> getUserMemories(Long userId);

    void deactivateMemory(Long memoryId);

    List<AiUserMemory> recallMemories(Long userId, String query, int limit);

    void saveMemoryCandidate(Long userId, String text, String source);

    /** 画像引导专用：直接保存用户选项回答为记忆，跳过去重检查 */
    void saveProfileAnswer(Long userId, String text, String memoryType);

    /** 聊天模式保存记忆：停用同类型旧记忆，插入新记忆（每类只保留最新） */
    void saveChatMemory(Long userId, String text, String memoryType);

    boolean hasCompleteProfile(Long userId);

    List<String> getMissingProfileCategories(Long userId);

    int clearUserMemories(Long userId);
}
