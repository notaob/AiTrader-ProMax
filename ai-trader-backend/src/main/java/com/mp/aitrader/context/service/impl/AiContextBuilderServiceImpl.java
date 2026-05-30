package com.mp.aitrader.context.service.impl;

import com.mp.aitrader.context.domain.AiContextLog;
import com.mp.aitrader.context.mapper.AiContextLogMapper;
import com.mp.aitrader.context.service.AiContextBuilderService;
import com.mp.aitrader.conversation.domain.AiConversationSummary;
import com.mp.aitrader.conversation.domain.AiMessage;
import com.mp.aitrader.conversation.domain.AiSessionState;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.conversation.service.AiSessionStateService;
import com.mp.aitrader.conversation.service.AiSummaryService;
import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import com.mp.aitrader.knowledge.service.AiKnowledgeService;
import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.service.AiMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiContextBuilderServiceImpl implements AiContextBuilderService {

    @Autowired
    private AiConversationService conversationService;

    @Autowired
    private AiSessionStateService sessionStateService;

    @Autowired
    private AiMemoryService memoryService;

    @Autowired
    private AiKnowledgeService knowledgeService;

    @Autowired
    private AiSummaryService summaryService;

    @Autowired
    private AiContextLogMapper contextLogMapper;

    @Override
    public Map<String, Object> buildContext(Long conversationId, Long userId, String message, String sceneType) {
        Map<String, Object> context = new HashMap<>();

        List<AiMessage> recentMessages = conversationService.getConversationMessages(conversationId)
                .stream()
                .map(dto -> {
                    AiMessage msg = new AiMessage();
                    msg.setRole(dto.getRole());
                    msg.setContent(dto.getContent());
                    msg.setMessageIndex(dto.getMessageIndex());
                    msg.setCreatedAt(dto.getCreatedAt());
                    return msg;
                })
                .collect(Collectors.toList());

        AiSessionState sessionState = sessionStateService.getSessionState(conversationId);
        AiConversationSummary latestSummary = summaryService.getLatestSummary(conversationId);

        List<AiUserMemory> recalledMemories = memoryService.recallMemories(userId, message, 5);
        List<AiKnowledgeChunk> knowledgeChunks = knowledgeService.searchKnowledge(message, 3);

        List<Map<String, String>> history = recentMessages.stream()
                .map(msg -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", msg.getRole());
                    map.put("content", msg.getContent());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> state = new HashMap<>();
        if (sessionState != null) {
            state.put("current_mode", sessionState.getCurrentMode());
            state.put("current_step", sessionState.getCurrentStep());
            state.put("current_intent", sessionState.getCurrentIntent());
            if (sessionState.getStateJson() != null) {
                state.put("state_json", sessionState.getStateJson());
            }
        }

        List<String> summaries = new ArrayList<>();
        if (latestSummary != null) {
            summaries.add(latestSummary.getSummaryText());
        }

        List<String> memoryContents = recalledMemories.stream()
                .map(AiUserMemory::getContent)
                .collect(Collectors.toList());

        List<String> knowledgeTexts = knowledgeChunks.stream()
                .map(AiKnowledgeChunk::getChunkText)
                .collect(Collectors.toList());

        context.put("conversation_id", conversationId);
        context.put("user_id", userId);
        context.put("scene_type", sceneType);
        context.put("history", history);
        context.put("state", state);
        context.put("summaries", summaries);
        context.put("memories", memoryContents);
        context.put("knowledge", knowledgeTexts);
        context.put("current_message", message);

        log.info("构建上下文完成，conversationId={}, memories={}, knowledge={}",
                conversationId, memoryContents.size(), knowledgeTexts.size());

        return context;
    }

    @Override
    public void logContext(AiContextLog log) {
        contextLogMapper.insert(log);
    }
}
