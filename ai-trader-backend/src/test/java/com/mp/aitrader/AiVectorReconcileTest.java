package com.mp.aitrader;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.mapper.AiUserMemoryMapper;
import com.mp.aitrader.metric.AiMetrics;
import com.mp.aitrader.task.AiTaskPublisher;
import com.mp.aitrader.task.AiTaskRabbitConfig;
import com.mp.aitrader.task.AiVectorReconcileService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T4 第三道兜底验证：向量对账。
 *
 * <p>三层兜底各自的覆盖面：MQ 重试兜"投了但消费失败"，DLQ 兜"反复失败"，而
 * <b>消息根本没投出去</b>（降级本地执行时进程重启、兜底执行也失败）只有对账能兜住。
 *
 * <p>判据：MySQL 活跃记忆在 Redis 无 {@code mem:doc:{id}} 键 → 必须重新投递向量写入；
 * 键存在 → 不投；Redis 不可用 → 中止而非误判全量缺失。
 *
 * <p>纯单元测试（Mockito），不需要 MySQL / Redis / broker。
 */
@ExtendWith(MockitoExtension.class)
class AiVectorReconcileTest {

    @Mock
    private AiUserMemoryMapper userMemoryMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AiTaskPublisher taskPublisher;

    @Mock
    private LangGraphClient langGraphClient;

    private AiMetrics metrics;
    private AiVectorReconcileService service;

    @BeforeEach
    void setUp() {
        service = new AiVectorReconcileService();
        metrics = new AiMetrics(new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "userMemoryMapper", userMemoryMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "taskPublisher", taskPublisher);
        ReflectionTestUtils.setField(service, "langGraphClient", langGraphClient);
        ReflectionTestUtils.setField(service, "metrics", metrics);
    }

    private AiUserMemory memory(long id, long userId, String type) {
        AiUserMemory m = new AiUserMemory();
        m.setId(id);
        m.setUserId(userId);
        m.setMemoryType(type);
        m.setContent("记忆 " + id);
        m.setIsActive(1);
        return m;
    }

    @Test
    void shouldResubmit_whenVectorKeyMissing() {
        List<AiUserMemory> batch = List.of(
                memory(11L, 1L, "preference"),
                memory(12L, 1L, "constraint"),
                memory(13L, 2L, "goal"));
        when(userMemoryMapper.selectActiveBatchAfterId(0L, 500)).thenReturn(batch);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        AiVectorReconcileService.ReconcileResult r = service.reconcile(null);

        assertEquals(3, r.getScanned());
        assertEquals(3, r.getMissing());
        assertEquals(3, r.getResubmitted());

        // 必须重新投递向量写入任务（doc id = MySQL 主键，消费端覆盖写，天然幂等）
        ArgumentCaptor<String> rk = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.mp.aitrader.task.AiTaskPayloads.VectorMemorySaveTask> payload =
                ArgumentCaptor.forClass(com.mp.aitrader.task.AiTaskPayloads.VectorMemorySaveTask.class);
        verify(taskPublisher, times(3)).submit(rk.capture(), payload.capture(), any());

        assertEquals(AiTaskRabbitConfig.RK_VECTOR_SAVE, rk.getValue());
        assertEquals(11L, payload.getAllValues().get(0).getMemoryId());
        assertEquals(13L, payload.getAllValues().get(2).getMemoryId());
        assertEquals(2L, payload.getAllValues().get(2).getUserId());
        assertEquals("goal", payload.getAllValues().get(2).getMemoryType());

        // 补投计数应体现在指标上
        assertEquals(3.0, metrics.counterValue("ai.vector.reconcile.resubmitted"));
    }

    @Test
    void shouldSkip_whenVectorKeyExists() {
        List<AiUserMemory> batch = List.of(memory(21L, 1L, "preference"));
        when(userMemoryMapper.selectActiveBatchAfterId(0L, 500)).thenReturn(batch);
        when(redisTemplate.hasKey("mem:doc:21")).thenReturn(true);

        AiVectorReconcileService.ReconcileResult r = service.reconcile(null);

        assertEquals(1, r.getScanned());
        assertEquals(0, r.getMissing());
        assertEquals(0, r.getResubmitted());
        verify(taskPublisher, times(0)).submit(anyString(), any(), any());
    }

    @Test
    void shouldAbort_whenRedisUnavailable() {
        when(userMemoryMapper.selectActiveBatchAfterId(0L, 500))
                .thenReturn(List.of(memory(31L, 1L, "preference")));
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        // ★ Redis 挂了必须中止，而不是把全部记忆误判为缺失、全量重推打爆 embedding 配额
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reconcile(null));
        assertTrue(e.getMessage().contains("Redis 不可用"));
        verify(taskPublisher, times(0)).submit(anyString(), any(), any());
    }

    @Test
    void shouldRespectMaxResubmitLimit() {
        List<AiUserMemory> batch = List.of(
                memory(41L, 1L, "preference"),
                memory(42L, 1L, "goal"));
        when(userMemoryMapper.selectActiveBatchAfterId(0L, 500)).thenReturn(batch);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // 上限 1：两条缺失只补投 1 条，但缺失总数仍如实统计为 2，且标记 truncated
        AiVectorReconcileService.ReconcileResult r = service.reconcile(1);

        assertEquals(2, r.getMissing());
        assertEquals(1, r.getResubmitted());
        assertTrue(r.isTruncated());
    }
}
