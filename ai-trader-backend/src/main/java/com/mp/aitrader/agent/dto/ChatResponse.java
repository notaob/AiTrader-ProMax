package com.mp.aitrader.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 回答内容
     */
    private String content;

    /**
     * 执行时间（毫秒）
     */
    private int executionTime;

    /**
     * 错误信息
     */
    private String error;
}
