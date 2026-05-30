package com.mp.aitrader.conversation.service.impl;

import com.mp.aitrader.conversation.domain.AiSessionState;
import com.mp.aitrader.conversation.mapper.AiSessionStateMapper;
import com.mp.aitrader.conversation.service.AiSessionStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AiSessionStateServiceImpl implements AiSessionStateService {

    @Autowired
    private AiSessionStateMapper sessionStateMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void initSessionState(Long conversationId) {
        AiSessionState state = new AiSessionState();
        state.setConversationId(conversationId);
        state.setCurrentMode("chat");
        state.setCurrentStep("collect_requirements");
        state.setCurrentIntent("general_chat");
        state.setStateJson("{}");
        sessionStateMapper.insert(state);
    }

    @Override
    public AiSessionState getSessionState(Long conversationId) {
        return sessionStateMapper.selectByConversationId(conversationId);
    }

    @Override
    public void updateSessionState(Long conversationId, String userMessage, String aiReply) {
        AiSessionState state = sessionStateMapper.selectByConversationId(conversationId);
        if (state == null) {
            return;
        }

        String message = userMessage.toLowerCase();
        Map<String, Object> stateMap = parseStateJson(state.getStateJson());
        Map<String, Object> confirmedSlots = (Map<String, Object>) stateMap.getOrDefault("confirmed_slots", new HashMap<>());
        List<String> pendingSlots = (List<String>) stateMap.getOrDefault("pending_slots", new ArrayList<>());

        if (message.contains("btc") || message.contains("bitcoin")) {
            confirmedSlots.put("symbol", "BTC");
        }
        if (message.contains("eth") || message.contains("ethereum")) {
            confirmedSlots.put("symbol", "ETH");
        }
        if (message.contains("sol") || message.contains("solana")) {
            confirmedSlots.put("symbol", "SOL");
        }

        if (message.contains("稳健") || message.contains("保守")) {
            confirmedSlots.put("risk_level", "稳健");
        }
        if (message.contains("激进") || message.contains("高风险")) {
            confirmedSlots.put("risk_level", "进取");
        }

        if (message.contains("短线")) {
            confirmedSlots.put("time_horizon", "短线");
        }
        if (message.contains("中线")) {
            confirmedSlots.put("time_horizon", "中线");
        }
        if (message.contains("长线")) {
            confirmedSlots.put("time_horizon", "长线");
        }

        if (message.contains("策略") || message.contains("分析")) {
            state.setCurrentMode("strategy");
            state.setCurrentIntent("strategy_analysis");
        }

        if (confirmedSlots.containsKey("symbol") && confirmedSlots.containsKey("risk_level")) {
            state.setCurrentStep("generate_strategy");
        }

        stateMap.put("confirmed_slots", confirmedSlots);
        stateMap.put("pending_slots", pendingSlots);

        try {
            state.setStateJson(objectMapper.writeValueAsString(stateMap));
        } catch (Exception e) {
            log.error("序列化状态JSON失败", e);
        }

        sessionStateMapper.update(state);
    }

    @Override
    public void updateSessionStateFromPatch(Long conversationId, Map<String, Object> statePatch) {
        AiSessionState state = sessionStateMapper.selectByConversationId(conversationId);
        if (state == null) {
            return;
        }

        if (statePatch.containsKey("current_mode")) {
            state.setCurrentMode((String) statePatch.get("current_mode"));
        }
        if (statePatch.containsKey("current_step")) {
            state.setCurrentStep((String) statePatch.get("current_step"));
        }
        if (statePatch.containsKey("current_intent")) {
            state.setCurrentIntent((String) statePatch.get("current_intent"));
        }
        if (statePatch.containsKey("state_json")) {
            try {
                state.setStateJson(objectMapper.writeValueAsString(statePatch.get("state_json")));
            } catch (Exception e) {
                log.error("序列化状态JSON失败", e);
            }
        }

        sessionStateMapper.update(state);
    }

    private Map<String, Object> parseStateJson(String stateJson) {
        try {
            if (stateJson == null || stateJson.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(stateJson, HashMap.class);
        } catch (Exception e) {
            log.error("解析状态JSON失败", e);
            return new HashMap<>();
        }
    }
}
