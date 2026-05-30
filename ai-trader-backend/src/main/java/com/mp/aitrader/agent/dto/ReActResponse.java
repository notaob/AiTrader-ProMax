package com.mp.aitrader.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct 响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 最终答案
     */
    private String finalAnswer;

    /**
     * 思考链
     */
    private String chainOfThought;

    /**
     * 迭代次数
     */
    private int iterations;

    /**
     * 执行时间（毫秒）
     */
    private int executionTime;

    /**
     * 错误信息
     */
    private String error;
}
