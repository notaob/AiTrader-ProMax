package com.mp.aitrader.task;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.conversation.service.AiSummaryService;
import com.mp.aitrader.memory.service.AiMemoryService;
import com.mp.aitrader.task.AiTaskPayloads.MemoryPersistTask;
import com.mp.aitrader.task.AiTaskPayloads.RagSyncTask;
import com.mp.aitrader.task.AiTaskPayloads.SummaryTask;
import com.mp.aitrader.task.AiTaskPayloads.VectorMemoryDeleteTask;
import com.mp.aitrader.task.AiTaskPayloads.VectorMemorySaveTask;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 后台任务消费者。
 *
 * <p>统一约定：
 * <ul>
 *   <li><b>手动 ack</b>：业务成功才确认；失败 {@code basicNack(requeue=false)}，
 *       交给 DLX 进死信队列 —— 既不无限重投毒消息，也不会像原来那样 {@code log.warn} 一声就永久丢失。</li>
 *   <li><b>幂等</b>：所有任务都按"重复消费无副作用"设计（向量 doc id = MySQL 主键 → 覆盖写）。</li>
 *   <li><b>记忆持久化强制单消费者</b>：写入是"先查重/停用 → 再插入"的非原子序列，
 *       并发会写出重复记忆或让两条 constraint 并存（业务要求 constraint 只保留最新）。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.task.mq.enabled", havingValue = "true")
public class AiTaskConsumers {

    @Autowired
    private AiSummaryService summaryService;

    @Autowired
    private AiMemoryService memoryService;

    @Autowired
    private LangGraphClient langGraphClient;

    @Autowired
    private com.mp.aitrader.metric.AiMetrics aiMetrics;

    @RabbitListener(queues = AiTaskRabbitConfig.Q_SUMMARY, ackMode = "MANUAL")
    public void onSummary(SummaryTask task, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle("summary", channel, tag, () ->
                summaryService.generateAndSaveSummary(task.getConversationId()));
    }

    /** concurrency=1：单消费者串行，保证同一用户的查重/停用/插入顺序正确。 */
    @RabbitListener(queues = AiTaskRabbitConfig.Q_MEMORY_PERSIST, ackMode = "MANUAL", concurrency = "1")
    public void onMemoryPersist(MemoryPersistTask task, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle("memory.persist", channel, tag, () ->
                memoryService.saveChatMemory(task.getUserId(), task.getContent(), task.getMemoryType()));
    }

    @RabbitListener(queues = AiTaskRabbitConfig.Q_VECTOR_SAVE, ackMode = "MANUAL")
    public void onVectorSave(VectorMemorySaveTask task, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle("vector.save", channel, tag, () -> {
            Map<String, Object> item = new HashMap<>();
            item.put("memory_id", task.getMemoryId());
            item.put("user_id", task.getUserId());
            item.put("content", task.getContent());
            item.put("memory_type", task.getMemoryType());
            if (!langGraphClient.syncMemorySave(task.getUserId(), Collections.singletonList(item))) {
                throw new IllegalStateException("Python 返回写入失败: memoryId=" + task.getMemoryId());
            }
        });
    }

    @RabbitListener(queues = AiTaskRabbitConfig.Q_VECTOR_DELETE, ackMode = "MANUAL")
    public void onVectorDelete(VectorMemoryDeleteTask task, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle("vector.delete", channel, tag, () -> {
            if (!langGraphClient.syncMemoryDelete(task.getUserId(), task.getMemoryIds(), task.getMemoryType())) {
                throw new IllegalStateException("Python 返回删除失败: userId=" + task.getUserId());
            }
        });
    }

    @RabbitListener(queues = AiTaskRabbitConfig.Q_RAG_SYNC, ackMode = "MANUAL")
    public void onRagSync(RagSyncTask task, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle("rag.sync", channel, tag, () -> {
            if (!langGraphClient.syncChunksToVectorStore(task.getChunks(), task.getUserId())) {
                throw new IllegalStateException("Python 返回同步失败: userId=" + task.getUserId());
            }
        });
    }

    private void handle(String kind, Channel channel, long tag, Runnable action) throws IOException {
        try {
            action.run();
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[ai-task] {} 消费失败，转入死信队列: {}", kind, e.getMessage(), e);
            // requeue=false：避免毒消息无限重投；由 x-dead-letter-exchange 转入 *.dlq 供人工重放
            aiMetrics.incrementDlq(kind);
            channel.basicNack(tag, false, false);
        }
    }
}
