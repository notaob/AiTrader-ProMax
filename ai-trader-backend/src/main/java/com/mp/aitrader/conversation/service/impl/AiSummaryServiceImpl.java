package com.mp.aitrader.conversation.service.impl;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.conversation.domain.AiConversationSummary;
import com.mp.aitrader.conversation.domain.AiMessage;
import com.mp.aitrader.conversation.mapper.AiConversationSummaryMapper;
import com.mp.aitrader.conversation.mapper.AiMessageMapper;
import com.mp.aitrader.conversation.service.AiSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private AiConversationSummaryMapper summaryMapper;

    @Autowired
    private LangGraphClient langGraphClient;

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

    /**
     * Stage2：摘要文本由 Python /agent/summarize 生成（LLM 语义摘要，去 <think>）。
     * Python 不可用时回退本地拼接截断，保证摘要永远非空。
     */
    private String generateSummaryText(List<AiMessage> messages) {
        List<Map<String, String>> payload = messages.stream()
                .map(msg -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    return m;
                })
                .collect(Collectors.toList());

        String summary = langGraphClient.summarizeMessages(payload);
        if (summary == null || summary.isBlank()) {
            log.warn("Python 摘要不可用，回退本地摘要");
            return legacyTruncatedSummary(messages);
        }
        return summary;
    }

    /** 本地兜底：拼接 + 截断（原实现），Python 服务异常时保证不中断。 */
    private String legacyTruncatedSummary(List<AiMessage> messages) {
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
