package com.mp.aitrader.conversation.service.impl;

import com.mp.aitrader.conversation.domain.*;
import com.mp.aitrader.conversation.dto.*;
import com.mp.aitrader.conversation.mapper.*;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.conversation.service.AiSessionStateService;
import com.mp.aitrader.conversation.service.AiSummaryService;
import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.agent.dto.LangGraphChatResult;
import com.mp.aitrader.agent.dto.TypedMemoryCandidate;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.service.AiMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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

    @Autowired
    private AiMemoryService memoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // 策略报告模式：检查画像完整性，不完整则降级为 chat 模式先收集画像
        Integer remainingChance = null;
        boolean profileIncomplete = false;
        List<String> missingCategories = new ArrayList<>();

        if ("strategy".equals(mode)) {
            missingCategories = memoryService.getMissingProfileCategories(userId);
            if (!missingCategories.isEmpty()) {
                // 画像不完整：降级为 chat 模式，先引导用户填写画像，不扣 AI 机会
                profileIncomplete = true;
                mode = "chat";
                log.info("用户 {} 画像不完整（缺失: {}），策略报告降级为聊天引导模式", userId, missingCategories);
            } else {
                // 画像完整：正常扣减 AI 机会
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
        }

        Integer maxIndex = messageMapper.selectMaxMessageIndex(conversationId);
        int nextIndex = (maxIndex != null ? maxIndex : 0) + 1;

        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setMessageIndex(nextIndex);
        messageMapper.insert(userMsg);

        // 画像不完整：直接返回引导问题和选项，不调用 AI
        if (profileIncomplete) {
            // 读取 session state 中的 pending_profile_category
            AiSessionState sessionState = sessionStateMapper.selectByConversationId(conversationId);
            String pendingCategory = null;
            Map<String, Object> stateMap = new HashMap<>();

            if (sessionState != null && sessionState.getStateJson() != null) {
                try {
                    stateMap = objectMapper.readValue(sessionState.getStateJson(),
                            new TypeReference<Map<String, Object>>() {});
                    Object pending = stateMap.get("pending_profile_category");
                    if (pending != null) {
                        pendingCategory = pending.toString();
                    }
                } catch (Exception e) {
                    log.warn("解析 stateJson 失败: {}", e.getMessage());
                }
            }

            // 如果有待回答的类别，且用户消息不是初始触发语，直接保存为记忆
            if (pendingCategory != null && !userMessage.contains("策略报告") && !userMessage.contains("市场数据")) {
                try {
                    memoryService.saveProfileAnswer(userId, userMessage, pendingCategory);
                    log.info("画像引导: 保存用户选项回答为记忆 [{}]: {}", pendingCategory, userMessage);
                } catch (Exception e) {
                    log.warn("画像引导记忆保存失败: {}", e.getMessage());
                }
                // 清除 pending_category
                stateMap.remove("pending_profile_category");
            }

            // 重新检查缺失类别（因为刚刚可能保存了新记忆）
            missingCategories = memoryService.getMissingProfileCategories(userId);
            if (missingCategories.isEmpty()) {
                // 画像完整，清除 pending 状态
                if (sessionState != null) {
                    try {
                        stateMap.remove("pending_profile_category");
                        sessionState.setStateJson(objectMapper.writeValueAsString(stateMap));
                        sessionStateMapper.update(sessionState);
                    } catch (Exception e) {
                        log.warn("更新 stateJson 失败: {}", e.getMessage());
                    }
                }

                String doneMsg = "太好了！你的交易画像已经完整，现在可以重新点击「获取策略报告」来生成个性化报告了。";
                Integer doneMaxIndex = messageMapper.selectMaxMessageIndex(conversationId);
                int doneAssistantIndex = (doneMaxIndex != null ? doneMaxIndex : 0) + 1;

                AiMessage doneAssistantMsg = new AiMessage();
                doneAssistantMsg.setConversationId(conversationId);
                doneAssistantMsg.setRole("assistant");
                doneAssistantMsg.setContent(doneMsg);
                doneAssistantMsg.setMessageIndex(doneAssistantIndex);
                messageMapper.insert(doneAssistantMsg);

                AiConversation doneConversation = conversationMapper.selectById(conversationId);
                doneConversation.setLastMessageAt(LocalDateTime.now());
                conversationMapper.update(doneConversation);

                return ChatResponse.builder()
                        .reply(doneMsg)
                        .conversationId(conversationId)
                        .build();
            }

            String category = missingCategories.get(0);
            String question;
            List<ProfileOption> options;
            String categoryKey;

            switch (category) {
                case "交易偏好":
                    question = "为了给你生成个性化的策略报告，我需要先了解你的交易风格。你的交易风格偏向哪种？";
                    categoryKey = "preference";
                    options = List.of(
                            ProfileOption.builder().label("日内短线").value("我的交易风格是日内短线，持仓时间通常不超过一天").build(),
                            ProfileOption.builder().label("波段交易（数天到数周）").value("我的交易风格是波段交易，持仓时间通常几天到几周").build(),
                            ProfileOption.builder().label("中长线（月级别以上）").value("我的交易风格是中长线，持仓时间通常一个月以上").build()
                    );
                    break;
                case "交易目标":
                    question = "了解！那你的交易目标是什么？";
                    categoryKey = "goal";
                    options = List.of(
                            ProfileOption.builder().label("稳定收益，控制回撤").value("我的交易目标是追求稳定收益，优先控制回撤").build(),
                            ProfileOption.builder().label("资产快速增长").value("我的交易目标是资产快速增长，可以接受较大波动").build(),
                            ProfileOption.builder().label("长期价值积累").value("我的交易目标是长期价值积累，不追求短期暴利").build()
                    );
                    break;
                case "风控规则":
                    question = "好的，最后关于风控，你通常怎么管理风险？";
                    categoryKey = "constraint";
                    options = List.of(
                            ProfileOption.builder().label("固定止损（如每笔3-5%）").value("我的风控规则是设置固定止损，每笔交易止损控制在3-5%").build(),
                            ProfileOption.builder().label("根据技术位设止损").value("我的风控规则是根据支撑阻力等技术位设置止损").build(),
                            ProfileOption.builder().label("主要靠仓位控制").value("我的风控规则是主要通过控制仓位大小来管理风险").build(),
                            ProfileOption.builder().label("还没有明确规则").value("我目前还没有明确的风控规则，需要建议").build()
                    );
                    break;
                default:
                    question = "请告诉我更多关于你的交易信息。";
                    categoryKey = "preference";
                    options = List.of();
            }

            // 保存引导问题为 assistant 消息
            Integer newMaxIndex = messageMapper.selectMaxMessageIndex(conversationId);
            int assistantIndex = (newMaxIndex != null ? newMaxIndex : 0) + 1;
            AiMessage assistantMsg = new AiMessage();
            assistantMsg.setConversationId(conversationId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(question);
            assistantMsg.setMessageIndex(assistantIndex);
            messageMapper.insert(assistantMsg);

            // 将 pending_profile_category 写入 stateJson
            if (sessionState != null) {
                try {
                    stateMap.put("pending_profile_category", categoryKey);
                    sessionState.setStateJson(objectMapper.writeValueAsString(stateMap));
                    sessionStateMapper.update(sessionState);
                } catch (Exception e) {
                    log.warn("写入 pending_profile_category 失败: {}", e.getMessage());
                }
            }

            AiConversation conversation = conversationMapper.selectById(conversationId);
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationMapper.update(conversation);

            log.info("画像引导: 用户 {} 缺失={}, pending_category={}, 返回选项", userId, category, categoryKey);
            return ChatResponse.builder()
                    .reply(question)
                    .conversationId(conversationId)
                    .profileOptions(options)
                    .build();
        }

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

        // 召回用户长期记忆
        List<String> memoryTexts;
        boolean isStrategy = "strategy".equals(mode);

        if (isStrategy) {
            // 策略模式：拉取所有活跃记忆（模板消息无法做关键词匹配）
            List<AiUserMemory> allMemories = memoryService.getUserMemories(userId);
            memoryTexts = allMemories.stream()
                    .map(AiUserMemory::getContent)
                    .collect(Collectors.toList());
            log.info("策略模式为用户 {} 加载全部 {} 条记忆", userId, memoryTexts.size());
        } else {
            // 聊天模式：基于消息内容关键词召回
            List<AiUserMemory> userMemories = memoryService.recallMemories(userId, userMessage, 5);
            memoryTexts = userMemories.stream()
                    .map(AiUserMemory::getContent)
                    .collect(Collectors.toList());
            log.info("聊天模式为用户 {} 召回 {} 条记忆", userId, memoryTexts.size());
        }

        LangGraphChatResult chatResult = langGraphClient.chatWithContext(
                userMessage,
                userId.toString(),
                "conversation_" + conversationId,
                history,
                state,
                summaries,
                memoryTexts,
                mode
        );

        String reply = chatResult.getAnswer();

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

        // 记忆处理：仅 chat 模式用 AI 分类结果保存记忆（每类只保留最新），strategy 模式跳过
        if (!isStrategy) {
            try {
                List<TypedMemoryCandidate> typedCandidates = chatResult.getTypedMemoryCandidates();
                if (typedCandidates != null && !typedCandidates.isEmpty()) {
                    for (TypedMemoryCandidate candidate : typedCandidates) {
                        memoryService.saveChatMemory(userId, candidate.getContent(), candidate.getMemoryType());
                    }
                    log.info("为用户 {} 保存 {} 条 AI 分类记忆", userId, typedCandidates.size());
                }
            } catch (Exception e) {
                log.warn("记忆持久化异常，不影响对话结果: {}", e.getMessage());
            }
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
