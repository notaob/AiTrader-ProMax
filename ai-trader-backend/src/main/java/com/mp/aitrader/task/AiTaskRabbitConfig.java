package com.mp.aitrader.task;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 后台任务的 MQ 拓扑。
 *
 * <p>仅在 {@code ai.task.mq.enabled=true} 时生效 —— 未部署 RabbitMQ 时整套不注册，
 * 任务自动降级为本地执行（见 {@link AiTaskPublisher}），不会因缺少 broker 导致应用起不来。
 *
 * <p>拓扑：
 * <pre>
 * ai.topic (topic)
 *   conversation.summary   → ai.conversation.summary
 *   memory.persist         → ai.memory.persist      （单消费者串行，保证去重正确）
 *   vector.memory.save     → ai.vector.memory.save
 *   vector.memory.delete   → ai.vector.memory.delete
 *   vector.rag.sync        → ai.vector.rag.sync
 *        └─ 消费失败（basicNack requeue=false）→ ai.dlx → *.dlq（人工可重放）
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "ai.task.mq.enabled", havingValue = "true")
public class AiTaskRabbitConfig {

    public static final String EXCHANGE = "ai.topic";
    public static final String DLX = "ai.dlx";

    /**
     * JSON 消息转换器（发布与消费共用，Boot 自动装配到 RabbitTemplate 与监听容器）。
     *
     * <p><b>不配它的后果（DLQ 实测真实踩中）</b>：默认 SimpleMessageConverter 只支持
     * String / byte[] / Serializable —— 任务 payload 类不是 Serializable，
     * <b>每次 publish 都抛 IllegalArgumentException</b>，再被降级逻辑吞成本地执行：
     * 表面一切正常，MQ 实际从未承载过一条消息。单测 mock 了 RabbitTemplate，测不出这类装配问题，
     * 只有真 broker 的运行时验收能暴露。
     */
    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jacksonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }

    public static final String RK_SUMMARY = "conversation.summary";
    public static final String RK_MEMORY_PERSIST = "memory.persist";
    public static final String RK_VECTOR_SAVE = "vector.memory.save";
    public static final String RK_VECTOR_DELETE = "vector.memory.delete";
    public static final String RK_RAG_SYNC = "vector.rag.sync";

    public static final String Q_SUMMARY = "ai.conversation.summary";
    public static final String Q_MEMORY_PERSIST = "ai.memory.persist";
    public static final String Q_VECTOR_SAVE = "ai.vector.memory.save";
    public static final String Q_VECTOR_DELETE = "ai.vector.memory.delete";
    public static final String Q_RAG_SYNC = "ai.vector.rag.sync";

    @Bean
    public TopicExchange aiExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange aiDeadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    // ---------- 业务队列（都带死信配置） ----------

    @Bean
    public Queue summaryQueue() {
        return durable(Q_SUMMARY);
    }

    @Bean
    public Queue memoryPersistQueue() {
        return durable(Q_MEMORY_PERSIST);
    }

    @Bean
    public Queue vectorSaveQueue() {
        return durable(Q_VECTOR_SAVE);
    }

    @Bean
    public Queue vectorDeleteQueue() {
        return durable(Q_VECTOR_DELETE);
    }

    @Bean
    public Queue ragSyncQueue() {
        return durable(Q_RAG_SYNC);
    }

    // ---------- 绑定 ----------

    @Bean
    public Binding bindSummary() {
        return BindingBuilder.bind(summaryQueue()).to(aiExchange()).with(RK_SUMMARY);
    }

    @Bean
    public Binding bindMemoryPersist() {
        return BindingBuilder.bind(memoryPersistQueue()).to(aiExchange()).with(RK_MEMORY_PERSIST);
    }

    @Bean
    public Binding bindVectorSave() {
        return BindingBuilder.bind(vectorSaveQueue()).to(aiExchange()).with(RK_VECTOR_SAVE);
    }

    @Bean
    public Binding bindVectorDelete() {
        return BindingBuilder.bind(vectorDeleteQueue()).to(aiExchange()).with(RK_VECTOR_DELETE);
    }

    @Bean
    public Binding bindRagSync() {
        return BindingBuilder.bind(ragSyncQueue()).to(aiExchange()).with(RK_RAG_SYNC);
    }

    // ---------- 死信队列：每个业务队列一个，便于定位与重放 ----------

    @Bean
    public Queue summaryDlq() {
        return QueueBuilder.durable(dlq(Q_SUMMARY)).build();
    }

    @Bean
    public Queue memoryPersistDlq() {
        return QueueBuilder.durable(dlq(Q_MEMORY_PERSIST)).build();
    }

    @Bean
    public Queue vectorSaveDlq() {
        return QueueBuilder.durable(dlq(Q_VECTOR_SAVE)).build();
    }

    @Bean
    public Queue vectorDeleteDlq() {
        return QueueBuilder.durable(dlq(Q_VECTOR_DELETE)).build();
    }

    @Bean
    public Queue ragSyncDlq() {
        return QueueBuilder.durable(dlq(Q_RAG_SYNC)).build();
    }

    @Bean
    public Binding bindSummaryDlq() {
        return BindingBuilder.bind(summaryDlq()).to(aiDeadLetterExchange()).with(dlq(Q_SUMMARY));
    }

    @Bean
    public Binding bindMemoryPersistDlq() {
        return BindingBuilder.bind(memoryPersistDlq()).to(aiDeadLetterExchange()).with(dlq(Q_MEMORY_PERSIST));
    }

    @Bean
    public Binding bindVectorSaveDlq() {
        return BindingBuilder.bind(vectorSaveDlq()).to(aiDeadLetterExchange()).with(dlq(Q_VECTOR_SAVE));
    }

    @Bean
    public Binding bindVectorDeleteDlq() {
        return BindingBuilder.bind(vectorDeleteDlq()).to(aiDeadLetterExchange()).with(dlq(Q_VECTOR_DELETE));
    }

    @Bean
    public Binding bindRagSyncDlq() {
        return BindingBuilder.bind(ragSyncDlq()).to(aiDeadLetterExchange()).with(dlq(Q_RAG_SYNC));
    }

    private static Queue durable(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", dlq(name))
                .build();
    }

    private static String dlq(String queueName) {
        return queueName + ".dlq";
    }
}
