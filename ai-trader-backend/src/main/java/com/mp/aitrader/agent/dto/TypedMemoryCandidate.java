package com.mp.aitrader.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python AI 端分类后的记忆候选
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypedMemoryCandidate {
    /** 记忆内容 */
    private String content;
    /** 分类: preference / goal / constraint */
    private String memoryType;
}
