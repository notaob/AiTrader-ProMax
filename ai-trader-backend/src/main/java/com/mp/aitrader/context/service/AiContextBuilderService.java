package com.mp.aitrader.context.service;

import com.mp.aitrader.context.domain.AiContextLog;

import java.util.Map;

public interface AiContextBuilderService {

    Map<String, Object> buildContext(Long conversationId, Long userId, String message, String sceneType);

    void logContext(AiContextLog log);
}
