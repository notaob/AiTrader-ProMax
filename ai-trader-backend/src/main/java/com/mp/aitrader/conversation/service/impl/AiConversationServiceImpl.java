package com.mp.aitrader.conversation.service.impl;

import com.mp.aitrader.conversation.domain.*;
import com.mp.aitrader.conversation.dto.*;
import com.mp.aitrader.conversation.mapper.*;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.conversation.service.AiSessionStateService;
import com.mp.aitrader.conversation.service.AiSummaryService;
import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiConversationServiceImpl implements AiConversationService {

    @Autowired
    private AiConversationMapper conversationMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private AiSessionStateMapper sessionStateMapper;

    @Autowired
    private AiConversationSummaryMapper summaryMapper;

    @Autowired
    private AiSessionStateService sessionStateService;

    @Autowired
    private AiSummaryService summaryService;

    @Autowired
    private LangGraphClient langGraphClient;

    @Autowired
    private TbUserMapper userMapper;

    @Override
    @Transactional
    public ConversationResponse createConversation(Long userId, CreateConversationRequest request) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(request.getTitle() != null ? request.getTitle() : "新对话");
        conversation.setSceneType(request.getSceneType() != null ? request.getSceneType() : "chat");
        conversation.setStatus("active");
        conversation.setLastMessageAt(LocalDateTime.now());

        conversationMapper.insert(conversation);

        sessionStateService.initSessionState(conversation.getId());

        return mapToResponse(conversation);
    }

    @Override
    public List<ConversationResponse> getUserConversations(Long userId) {
        List<AiConversation> conversations = conversationMapper.selectByUserId(userId);
        return conversations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageResponse> getConversationMessages(Long conversationId) {
        List<AiMessage> messages = messageMapper.selectByConversationId(conversationId);
        return messages.stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatResponse chat(Long conversationId, Long userId, ChatMessageRequest request) {
        String userMessage = request.getMessage();
        String mode = request.getMode();

        // 策略报告模式：检查并扣减 AI 机会次数
        Integer remainingChance = null;
        if ("strategy".equals(mode)) {
            TbUser user = userMapper.selectById(userId);
            if (user == null) {
                return ChatResponse.builder()
                        .reply("用户不存在")
                        .conversationId(conversationId)
                        .build();
            }
            Integer aiChance = user.getAiChance() == null ? 0 : user.getAiChance();
            if (aiChance <= 0) {
                return ChatResponse.builder()
                        .reply("AI交易机会不足，请先获取机会")
                        .conversationId(conversationId)
                        .build();
            }
            user.setAiChance(aiChance - 1);
            user.setUpdateTime(new Date());
            userMapper.updateById(user);
            remainingChance = aiChance - 1;
        }

        Integer maxIndex = messageMapper.selectMaxMessageIndex(conversationId);
        int nextIndex = (maxIndex != null ? maxIndex : 0) + 1;

        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setMessageIndex(nextIndex);
        messageMapper.insert(userMsg);

        List<AiMessage> recentMessages = messageMapper.selectRecentMessages(conversationId, 8);
        AiSessionState sessionState = sessionStateMapper.selectByConversationId(conversationId);
        AiConversationSummary latestSummary = summaryMapper.selectLatestByConversationId(conversationId);

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

        String reply = langGraphClient.chatWithContext(
                userMessage,
                userId.toString(),
                "conversation_" + conversationId,
                history,
                state,
                summaries,
                mode
        );

        Integer newMaxIndex = messageMapper.selectMaxMessageIndex(conversationId);
        int assistantIndex = (newMaxIndex != null ? newMaxIndex : 0) + 1;

        AiMessage assistantMsg = new AiMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        assistantMsg.setMessageIndex(assistantIndex);
        messageMapper.insert(assistantMsg);

        sessionStateService.updateSessionState(conversationId, userMessage, reply);

        if (summaryService.shouldCreateSummary(conversationId)) {
            summaryService.generateAndSaveSummary(conversationId);
        }

        AiConversation conversation = conversationMapper.selectById(conversationId);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.update(conversation);

        return ChatResponse.builder()
                .reply(reply)
                .conversationId(conversationId)
                .remainingChance(remainingChance)
                .build();
    }

    @Override
    public SessionStateResponse getSessionState(Long conversationId) {
        AiSessionState state = sessionStateMapper.selectByConversationId(conversationId);
        if (state == null) {
            return null;
        }
        return SessionStateResponse.builder()
                .id(state.getId())
                .conversationId(state.getConversationId())
                .currentIntent(state.getCurrentIntent())
                .currentMode(state.getCurrentMode())
                .currentStep(state.getCurrentStep())
                .stateJson(state.getStateJson())
                .updatedAt(state.getUpdatedAt())
                .build();
    }

    private ConversationResponse mapToResponse(AiConversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .title(conversation.getTitle())
                .sceneType(conversation.getSceneType())
                .status(conversation.getStatus())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageResponse mapToMessageResponse(AiMessage message) {
        return MessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .messageIndex(message.getMessageIndex())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
