package com.mp.aitrader.task;

import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.mapper.AiUserMemoryMapper;
import com.mp.aitrader.metric.AiMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量对账补偿（T4 第三道兜底）。
 *
 * <p><b>三层兜底里它兜的是什么</b>：
 * <ol>
 *   <li>MQ 重试 —— 兜"投了但消费失败"；</li>
 *   <li>DLQ 死信重放 —— 兜"反复失败进死信"，可人工重放；</li>
 *   <li><b>本对账</b> —— 兜前两层都盖不住的：<b>消息根本没投出去</b>
 *       （降级本地执行时进程重启、兜底执行也失败、历史脏数据）。</li>
 * </ol>
 *
 * <p>原理：记忆向量以 doc id = MySQL 主键写在 Redis hash {@code mem:doc:{memory_id}}
 * （见 Python {@code memory_service.py} 的 {@code mem_vectors} 索引）。因此对账只需：
 * 扫描 MySQL 活跃记忆 → {@code EXISTS mem:doc:{id}} → 缺失则<b>重新投递向量写入任务</b>。
 * 重新投递天然幂等（doc id 相同是覆盖写），重复执行无副作用。
 *
 * <p><b>边界</b>：Python 侧降级为进程内存索引（无 Redis Stack）时键永远不存在，
 * 每次对账都会全量重推 —— 由 {@code maxResubmit} 上限与幂等性兜住，不会打坏系统。
 */
@Slf4j
@Service
public class AiVectorReconcileService {

    /** 与 Python memory_service.py 的 doc_prefix 保持一致（mem:doc:{memory_id}）。 */
    public static final String MEMORY_DOC_PREFIX = "mem:doc:";

    private static final int SCAN_BATCH = 500;
    private static final int DEFAULT_MAX_RESUBMIT = 500;

    @Autowired
    private AiUserMemoryMapper userMemoryMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AiTaskPublisher taskPublisher;

    @Autowired
    private com.mp.aitrader.agent.client.LangGraphClient langGraphClient;

    @Autowired
    private AiMetrics metrics;

    /** 对账结果摘要。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconcileResult {
        private int scanned;
        private int missing;
        private int resubmitted;
        private boolean truncated;
    }

    /**
     * 执行一次对账，返回摘要。可通过手动入口反复触发；每次最多补投 {@code maxResubmit} 条。
     *
     * @throws IllegalStateException Redis 不可用时中止 —— 无法判定缺失，宁可不做也不误判全量
     */
    public ReconcileResult reconcile(Integer maxResubmitLimit) {
        int maxResubmit = maxResubmitLimit != null && maxResubmitLimit > 0
                ? Math.min(maxResubmitLimit, DEFAULT_MAX_RESUBMIT * 10)
                : DEFAULT_MAX_RESUBMIT;

        int scanned = 0;
        int missing = 0;
        int resubmitted = 0;
        boolean truncated = false;
        long afterId = 0L;

        while (true) {
            List<AiUserMemory> batch = userMemoryMapper.selectActiveBatchAfterId(afterId, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (AiUserMemory memory : batch) {
                afterId = memory.getId();
                scanned++;
                Boolean exists;
                try {
                    exists = redisTemplate.hasKey(MEMORY_DOC_PREFIX + memory.getId());
                } catch (Exception e) {
                    throw new IllegalStateException("Redis 不可用，对账中止（已扫描 " + scanned + " 条，"
                            + "本轮补投 " + resubmitted + " 条）: " + e.getMessage(), e);
                }
                if (exists == null) {
                    // hasKey 在管道/事务里返回 null；本场景意味着连接异常，直接中止
                    throw new IllegalStateException("Redis 不可用，对账中止（已扫描 " + scanned + " 条，"
                            + "本轮补投 " + resubmitted + " 条）");
                }
                if (exists) {
                    continue;
                }
                missing++;
                if (resubmitted >= maxResubmit) {
                    truncated = true;
                    continue;   // 继续统计缺失数量，但不再补投
                }
                resubmitVectorSave(memory);
                resubmitted++;
            }
            if (batch.size() < SCAN_BATCH) {
                break;
            }
        }

        log.info("[ai-reconcile] 向量对账完成: scanned={}, missing={}, resubmitted={}, truncated={}",
                scanned, missing, resubmitted, truncated);
        return ReconcileResult.builder()
                .scanned(scanned).missing(missing).resubmitted(resubmitted).truncated(truncated)
                .build();
    }

    /** 补投一条向量写入：走与主链路完全相同的投递器（MQ / 本地兜底自动选择），消费端幂等。 */
    private void resubmitVectorSave(AiUserMemory memory) {
        Map<String, Object> item = new HashMap<>();
        item.put("memory_id", memory.getId());
        item.put("user_id", memory.getUserId());
        item.put("content", memory.getContent());
        item.put("memory_type", memory.getMemoryType());

        AiTaskPayloads.VectorMemorySaveTask task = AiTaskPayloads.VectorMemorySaveTask.builder()
                .userId(memory.getUserId())
                .memoryId(memory.getId())
                .content(memory.getContent())
                .memoryType(memory.getMemoryType())
                .build();

        taskPublisher.submit(AiTaskRabbitConfig.RK_VECTOR_SAVE, task, () -> {
            boolean ok = langGraphClient.syncMemorySave(memory.getUserId(), Collections.singletonList(item));
            if (!ok) {
                log.warn("[ai-reconcile] 补投的向量同步仍失败（将由下一次对账再次拾起）: memoryId={}",
                        memory.getId());
            }
        });
        metrics.incrementReconcileResubmit();
    }
}
