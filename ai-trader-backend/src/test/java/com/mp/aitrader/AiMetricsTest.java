package com.mp.aitrader;

import com.mp.aitrader.metric.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T9 可观测性验证：AI 链路的关键"系统在硬扛"信号必须收敛为指标，而不是只躺在日志里。
 *
 * <p>覆盖四类信号：
 * <ul>
 *   <li>闸门拒绝 / LLM 限流 —— 容量与配额信号</li>
 *   <li>死信计数（按 kind 分标签）—— 消息丢失告警的依据</li>
 *   <li>队列积压深度 Gauge —— 消费能力是否不足</li>
 * </ul>
 *
 * <p>纯单元测试（SimpleMeterRegistry），不需要任何中间件。
 */
class AiMetricsTest {

    private AiMetrics metrics;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiMetrics(registry);
    }

    @Test
    void countersShouldAccumulate() {
        metrics.incrementGateRejected();
        metrics.incrementGateRejected();
        metrics.incrementGateRejected();
        metrics.incrementLlmThrottled();
        metrics.incrementTaskRejected();

        assertEquals(3.0, registry.get("ai.chat.gate.rejected").counter().count());
        assertEquals(1.0, registry.get("ai.llm.throttled").counter().count());
        assertEquals(1.0, registry.get("ai.task.rejected").counter().count());
    }

    @Test
    void dlqCounterShouldTagByKind() {
        metrics.incrementDlq("vector.save");
        metrics.incrementDlq("vector.save");
        metrics.incrementDlq("summary");

        assertEquals(2.0, registry.get("ai.task.dlq").tag("kind", "vector.save").counter().count());
        assertEquals(1.0, registry.get("ai.task.dlq").tag("kind", "summary").counter().count());
    }

    @Test
    void queueDepthGaugeShouldReflectBacklog() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(10));
        metrics.bindQueueDepth("memory", executor);

        // 第 1 个任务卡住 worker，后续 2 个进入队列 → Gauge 应实时读到 2
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        executor.execute(() -> { });
        executor.execute(() -> { });

        long deadline = System.currentTimeMillis() + 2000;
        while (executor.getQueue().size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }
        Number depth = (Number) registry.get("ai.task.queue.depth").tag("kind", "memory").gauge().value();
        release.countDown();
        executor.shutdownNow();

        assertEquals(2, depth.intValue(), "3 个任务（1 个占住 worker）后，Gauge 应实时读到队列剩 2 个");
    }

    @Test
    void counterValueShouldResolveTaggedMeter() {
        metrics.incrementDlq("rag.sync");
        metrics.incrementDlq("rag.sync");

        assertEquals(2.0, metrics.counterValue("ai.task.dlq", "kind", "rag.sync"));
        // 未注册过的标签组合应直接报错，而不是静默返回 0 掩盖问题
        assertThrows(Exception.class, () -> metrics.counterValue("ai.task.dlq", "kind", "not-registered"));
    }
}
