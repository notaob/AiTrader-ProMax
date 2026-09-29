package com.mp.aitrader.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * AI 后台任务投递器。
 *
 * <p>核心职责三件事：
 * <ol>
 *   <li><b>事务安全</b>：在事务中投递会等到 {@code afterCommit} 之后，
 *       否则消费者可能早于提交读到数据（典型受害者：{@code uploadDocument} 这种事务内发消息）。</li>
 *   <li><b>可用性降级</b>：MQ 未启用或发送失败时，回退为本地执行 ——
 *       <b>绝不因为 MQ 故障让主链路失败</b>。</li>
 *   <li><b>不静默丢弃</b>：开启 MQ 后，失败的消息会进死信队列，可人工重放。</li>
 * </ol>
 */
@Slf4j
@Component
public class AiTaskPublisher {

    @Value("${ai.task.mq.enabled:false}")
    private boolean mqEnabled;

    /** required=false：未引入 amqp 或自动配置未生效时也能正常启动。 */
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    /**
     * 投递任务。
     *
     * @param routingKey 路由键（见 {@link AiTaskRabbitConfig}）
     * @param payload    消息体（需可序列化、且消费端幂等）
     * @param fallback   MQ 不可用 / 投递失败时的本地兜底执行逻辑
     */
    public void submit(String routingKey, Object payload, Runnable fallback) {
        Runnable task = () -> {
            if (mqEnabled && rabbitTemplate != null) {
                try {
                    rabbitTemplate.convertAndSend(AiTaskRabbitConfig.EXCHANGE, routingKey, payload);
                    return;
                } catch (Exception e) {
                    log.error("[ai-task] MQ 投递失败，降级为本地执行: rk={}", routingKey, e);
                }
            }
            runFallback(routingKey, fallback);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 事务内不立即发：等提交成功再投递，避免消费者读到未提交数据
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** MQ 是否启用（供调用方决定走队列还是本地线程池）。 */
    public boolean isMqEnabled() {
        return mqEnabled && rabbitTemplate != null;
    }

    private void runFallback(String routingKey, Runnable fallback) {
        if (fallback == null) {
            log.warn("[ai-task] 任务无本地兜底且 MQ 不可用，已丢弃: rk={}", routingKey);
            return;
        }
        try {
            fallback.run();
        } catch (Exception e) {
            log.error("[ai-task] 本地兜底执行失败: rk={}", routingKey, e);
        }
    }
}
