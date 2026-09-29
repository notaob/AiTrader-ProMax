package com.mp.aitrader.task;

import com.mp.aitrader.trace.TraceSupport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 后台任务执行器：承载"只影响下一次对话"的收尾任务（会话摘要、长期记忆持久化）。
 *
 * <p><b>为什么要有这个东西</b>：这些任务原本同步跑在 {@code persistAiReply} 里，
 * 而该方法位于 {@code sendDoneFrame} 之前 —— 用户 token 早已流完，却还要等
 * 1 次 LLM 摘要 + 2~3 次 embedding 调用才收到结束信号。
 *
 * <p><b>为什么记忆任务强制单线程</b>：记忆写入是"先查重/停用 → 再插入"的非原子序列。
 * 若同一用户的两条候选并发处理，会同时通过查重写出重复记忆，或让两条 constraint（风控规则）
 * 并存 —— 而业务要求 constraint 只保留最新一条。单线程即天然串行，零额外代码。
 *
 * <p><b>已知的取舍</b>：进程重启会丢失队列中的任务。摘要丢了最多是下次上下文略长，
 * 记忆丢了则永久召回不到 —— 后者由 T4（MQ 化 + 死信 + 对账）解决。
 */
@Slf4j
@Service
public class AiAsyncTaskService {

    /** 摘要：单次 LLM 调用，较慢；2 个线程足够，开多了反而会冲击 LLM 配额。 */
    private final ExecutorService summaryExecutor = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            namedFactory("ai-summary-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** 记忆：固定单线程，保证同一用户的"查重 → 停用 → 插入"严格串行。 */
    private final ExecutorService memoryExecutor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            namedFactory("ai-memory-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    private final AtomicLong rejected = new AtomicLong();

    @Autowired
    private com.mp.aitrader.metric.AiMetrics aiMetrics;

    /** 队列积压深度对外暴露为 Gauge，积压持续上涨说明消费能力不足。 */
    @jakarta.annotation.PostConstruct
    public void bindMetrics() {
        aiMetrics.bindQueueDepth("summary", (ThreadPoolExecutor) summaryExecutor);
        aiMetrics.bindQueueDepth("memory", (ThreadPoolExecutor) memoryExecutor);
    }

    private static ThreadFactory namedFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    @Autowired
    private AiTaskPublisher taskPublisher;

    /**
     * 提交摘要任务。
     * 开启 MQ 时投递到队列（进程重启不丢、失败进死信可重放）；否则走本地线程池。
     */
    public void submitSummary(Long conversationId, Runnable task) {
        if (taskPublisher.isMqEnabled()) {
            AiTaskPayloads.SummaryTask payload = AiTaskPayloads.SummaryTask.builder()
                    .conversationId(conversationId)
                    .build();
            taskPublisher.submit(AiTaskRabbitConfig.RK_SUMMARY, payload, task);
            return;
        }
        submit(summaryExecutor, "summary", task);
    }

    /** 提交记忆持久化任务（串行执行）。开启 MQ 时改为投递到队列。 */
    public void submitMemory(AiTaskPayloads.MemoryPersistTask payload, Runnable task) {
        if (taskPublisher.isMqEnabled()) {
            taskPublisher.submit(AiTaskRabbitConfig.RK_MEMORY_PERSIST, payload, task);
            return;
        }
        submit(memoryExecutor, "memory", task);
    }

    /** 已拒绝/降级为同步执行的任务数，供排查用。 */
    public long getRejectedCount() {
        return rejected.get();
    }

    private void submit(ExecutorService executor, String kind, Runnable task) {
        try {
            // 后台线程必须显式继承 MDC，否则这批日志没有 traceId，出问题无法串联
            executor.execute(TraceSupport.wrap(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    // 后台任务失败绝不能影响主链路，但必须留痕
                    log.error("[ai-async] {} 任务执行失败: {}", kind, e.getMessage(), e);
                }
            }));
        } catch (RejectedExecutionException e) {
            // 队列满：CallerRunsPolicy 会退化到当前线程；这里仅兜底统计
            rejected.incrementAndGet();
            aiMetrics.incrementTaskRejected();
            log.warn("[ai-async] {} 任务提交被拒绝，降级为同步执行", kind);
            try {
                task.run();
            } catch (Exception ex) {
                log.error("[ai-async] {} 任务同步降级执行失败: {}", kind, ex.getMessage(), ex);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        summaryExecutor.shutdown();
        memoryExecutor.shutdown();
        try {
            if (!summaryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                summaryExecutor.shutdownNow();
            }
            if (!memoryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                memoryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            summaryExecutor.shutdownNow();
            memoryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
