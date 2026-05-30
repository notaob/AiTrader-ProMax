package com.mp.aitrader.conversation.service.impl;

import com.mp.aitrader.conversation.domain.AiConversationSummary;
import com.mp.aitrader.conversation.domain.AiMessage;
import com.mp.aitrader.conversation.mapper.AiConversationSummaryMapper;
import com.mp.aitrader.conversation.mapper.AiMessageMapper;
import com.mp.aitrader.conversation.service.AiSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private AiConversationSummaryMapper summaryMapper;

    private static final int SUMMARY_THRESHOLD = 12;
    private static final int KEEP_RECENT = 6;

    @Override
    public boolean shouldCreateSummary(Long conversationId) {
        int messageCount = messageMapper.countByConversationId(conversationId);
        return messageCount >= SUMMARY_THRESHOLD;
    }

    @Override
    public AiConversationSummary getLatestSummary(Long conversationId) {
        return summaryMapper.selectLatestByConversationId(conversationId);
    }

    @Override
    public void generateAndSaveSummary(Long conversationId) {
        List<AiMessage> allMessages = messageMapper.selectByConversationId(conversationId);

        if (allMessages.size() < SUMMARY_THRESHOLD) {
            return;
        }

        int totalMessages = allMessages.size();
        int summaryEndIndex = totalMessages - KEEP_RECENT;

        if (summaryEndIndex <= 0) {
            return;
        }

        List<AiMessage> messagesToSummarize = allMessages.subList(0, summaryEndIndex);
        String summaryText = generateSummaryText(messagesToSummarize);

        AiConversationSummary summary = new AiConversationSummary();
        summary.setConversationId(conversationId);
        summary.setStartMessageIndex(0);
        summary.setEndMessageIndex(summaryEndIndex - 1);
        summary.setSummaryText(summaryText);
        summary.setSummaryType("rolling");

        summaryMapper.insert(summary);
        log.info("已为会话 {} 生成摘要，涵盖消息 {}-{}", conversationId, 0, summaryEndIndex - 1);
    }

    private String generateSummaryText(List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户此前对话摘要：");

        for (AiMessage msg : messages) {
            if ("user".equals(msg.getRole())) {
                sb.append("用户说：").append(msg.getContent()).append("；");
            } else if ("assistant".equals(msg.getRole())) {
                sb.append("AI回复：").append(msg.getContent().substring(0, Math.min(100, msg.getContent().length()))).append("...；");
            }
        }

        String result = sb.toString();
        if (result.length() > 500) {
            result = result.substring(0, 500) + "...(摘要已截断)";
        }
        return result;
    }
}
