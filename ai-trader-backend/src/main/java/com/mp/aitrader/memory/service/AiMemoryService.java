package com.mp.aitrader.memory.service;

import com.mp.aitrader.memory.domain.AiUserMemory;

import java.util.List;

public interface AiMemoryService {

    void extractMemory(Long userId, String userMessage, String aiResponse);

    List<AiUserMemory> getUserMemories(Long userId);

    void deactivateMemory(Long memoryId);

    List<AiUserMemory> recallMemories(Long userId, String query, int limit);
}
