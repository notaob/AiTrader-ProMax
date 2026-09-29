package com.mp.aitrader.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 后台任务的 MQ 消息体。
 *
 * <p>统一要求：<b>可序列化</b>（Jackson）+ <b>幂等</b>。
 * 幂等性是"至少一次投递"能安全工作的前提 —— 下面每条都已具备：
 * <ul>
 *   <li>向量类任务：doc id 就是 MySQL 主键，重复消费是覆盖写</li>
 *   <li>记忆持久化：写入前有去重判断，且建议加 {@code (user_id, memory_type, content_hash)} 唯一索引兜底</li>
 *   <li>摘要任务：消息体钉死会话 id，重复生成最多是覆盖同一区间的摘要</li>
 * </ul>
 */
public final class AiTaskPayloads {

    private AiTaskPayloads() {
    }

    /** 会话摘要：只带 id，由消费端回查（消息内容不可变，回查结果与投递时刻一致）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryTask {
        private Long conversationId;
    }

    /** 记忆持久化（preference / goal；constraint 走同步，不进队列）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryPersistTask {
        private Long userId;
        private String content;
        private String memoryType;
    }

    /** 记忆向量写入。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorMemorySaveTask {
        private Long userId;
        private Long memoryId;
        private String content;
        private String memoryType;
    }

    /** 记忆向量删除：优先 ids，其次 memoryType，均空则清空该用户全部向量。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorMemoryDeleteTask {
        private Long userId;
        private List<Long> memoryIds;
        private String memoryType;
    }

    /** 知识库分片向量化。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagSyncTask {
        private Long userId;
        private List<Map<String, Object>> chunks;
    }
}
