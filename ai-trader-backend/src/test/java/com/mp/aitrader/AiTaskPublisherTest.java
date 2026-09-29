package com.mp.aitrader;

import com.mp.aitrader.task.AiTaskPayloads;
import com.mp.aitrader.task.AiTaskPublisher;
import com.mp.aitrader.task.AiTaskRabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T4 验证：任务投递器的三个关键性质。
 *
 * <p>纯单元测试（Mockito），不需要 RabbitMQ broker / MySQL / Redis。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AiTaskPublisher publisher;

    private AiTaskPayloads.VectorMemorySaveTask payload;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "mqEnabled", true);
        payload = AiTaskPayloads.VectorMemorySaveTask.builder()
                .userId(1L).memoryId(2L).content("止损 5%").memoryType("constraint").build();
    }

    @Test
    void shouldSendToExchange_withCorrectRoutingKey() {
        publisher.submit(AiTaskRabbitConfig.RK_VECTOR_SAVE, payload, null);

        ArgumentCaptor<String> rk = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(AiTaskRabbitConfig.EXCHANGE), rk.capture(), body.capture());

        assertEquals(AiTaskRabbitConfig.RK_VECTOR_SAVE, rk.getValue());
        assertEquals(payload, body.getValue());
    }

    /**
     * 事务内投递必须推迟到提交之后 —— 否则消费者可能早于提交读到未落库的数据，
     * 典型受害者是 {@code uploadDocument}（@Transactional 内发消息）。
     */
    @Test
    void shouldDeferSending_untilTransactionCommitted() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.submit(AiTaskRabbitConfig.RK_RAG_SYNC, payload, null);

            // 提交前绝不能发出去
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertFalse(syncs.isEmpty(), "应注册事务同步回调");
            syncs.forEach(TransactionSynchronization::afterCommit);

            verify(rabbitTemplate).convertAndSend(eq(AiTaskRabbitConfig.EXCHANGE),
                    eq(AiTaskRabbitConfig.RK_RAG_SYNC), eq(payload));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** MQ 故障时降级本地执行：绝不因为 MQ 不可用让主链路失败。 */
    @Test
    void shouldRunFallback_whenSendFails() {
        doThrow(new IllegalStateException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        AtomicBoolean fallbackRan = new AtomicBoolean(false);

        publisher.submit(AiTaskRabbitConfig.RK_VECTOR_SAVE, payload, () -> fallbackRan.set(true));

        assertTrue(fallbackRan.get(), "投递失败时应降级为本地执行");
    }

    /** 未启用 MQ 时直接走本地执行（开箱行为与改造前一致）。 */
    @Test
    void shouldRunFallback_whenMqDisabled() {
        ReflectionTestUtils.setField(publisher, "mqEnabled", false);
        AtomicBoolean fallbackRan = new AtomicBoolean(false);

        publisher.submit(AiTaskRabbitConfig.RK_VECTOR_SAVE, payload, () -> fallbackRan.set(true));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        assertTrue(fallbackRan.get());
    }
}
