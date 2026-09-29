package com.mp.aitrader.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 链路业务指标（T9 可观测性收尾）。
 *
 * <p>改造前的问题：闸门拒绝、LLM 限流、任务队列积压、消息进死信 —— 这些"系统在硬扛"的信号
 * 全部只存在于日志里，出问题要靠人翻日志才能发现。现在统一收敛为 Micrometer 指标：
 * <ul>
 *   <li>{@code ai.chat.gate.rejected} — SSE 并发闸门拒绝次数（上涨 = 容量到了，考虑扩容）</li>
 *   <li>{@code ai.llm.throttled} — 收到 Python 侧限流 error 帧次数（上涨 = LLM 配额紧张）</li>
 *   <li>{@code ai.task.rejected} — 本地异步任务队列满被拒次数</li>
 *   <li>{@code ai.task.dlq} — 消息进死信次数（按 kind 分标签；&gt;0 就该人工重放）</li>
 *   <li>{@code ai.task.queue.depth} — 后台任务队列积压深度（Gauge，按 kind 分标签）</li>
 * </ul>
 *
 * <p>暴露方式：{@code GET /actuator/metrics/<name>}（JSON）与 {@code /actuator/prometheus}（抓取端点）。
 */
@Component
public class AiMetrics {

    private final MeterRegistry registry;
    private final Counter gateRejected;
    private final Counter llmThrottled;
    private final Counter taskRejected;
    private final Counter reconcileResubmit;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.gateRejected = Counter.builder("ai.chat.gate.rejected")
                .description("SSE 并发闸门拒绝的对话请求数")
                .register(registry);
        this.llmThrottled = Counter.builder("ai.llm.throttled")
                .description("收到 Python 侧限流 error 帧的次数")
                .register(registry);
        this.taskRejected = Counter.builder("ai.task.rejected")
                .description("本地异步任务队列满被拒（降级同步执行）的次数")
                .register(registry);
        this.reconcileResubmit = Counter.builder("ai.vector.reconcile.resubmitted")
                .description("对账任务累计补投的向量同步数（持续增长说明主链路有丢失源）")
                .register(registry);
    }

    public void incrementGateRejected() {
        gateRejected.increment();
    }

    public void incrementLlmThrottled() {
        llmThrottled.increment();
    }

    public void incrementTaskRejected() {
        taskRejected.increment();
    }

    public void incrementReconcileResubmit() {
        reconcileResubmit.increment();
    }

    public void incrementDlq(String kind) {
        registry.counter("ai.task.dlq", "kind", kind).increment();
    }

    /** 绑定后台任务队列积压深度（Gauge 会随查询实时求值，不额外占用资源）。 */
    public void bindQueueDepth(String kind, ThreadPoolExecutor executor) {
        Gauge.builder("ai.task.queue.depth", executor, e -> e.getQueue().size())
                .description("后台任务队列积压深度")
                .tag("kind", kind)
                .register(registry);
    }

    public double counterValue(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }
}
