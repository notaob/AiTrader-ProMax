package com.mp.aitrader.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LangGraph 对话返回结果
 * 包含 AI 回答和从对话中提取的记忆候选
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LangGraphChatResult {

    /**
     * AI 最终回答
     */
    private String answer;

    /**
     * Python 端从对话中提取的记忆候选文本列表（向后兼容）
     */
    private List<String> memoryCandidates;

    /**
     * Python AI 分类后的带类型记忆候选
     */
    private List<TypedMemoryCandidate> typedMemoryCandidates;
}
