package com.mp.aitrader.trace;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * traceId 支持：让一次请求的所有日志（含异步线程）都能串起来。
 *
 * <p><b>为什么需要显式传递</b>：MDC 基于 ThreadLocal，不会自动跨线程。
 * 本项目大量使用异步（SSE 编排线程、AI 后台任务线程池），
 * 这些线程里的日志如果没有 traceId，出问题根本无法把同一次请求的日志捞出来。
 *
 * <p>用法：提交到线程池之前用 {@link #wrap(Runnable)} 包一层。
 */
public final class TraceSupport {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceSupport() {
    }

    /** 生成短 traceId（16 位，足够定位且不会刷屏）。 */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null) {
            return task;
        }
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.setContextMap(context);
            try {
                task.run();
            } finally {
                restore(previous);
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null) {
            return task;
        }
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.setContextMap(context);
            try {
                return task.call();
            } finally {
                restore(previous);
            }
        };
    }

    private static void restore(Map<String, String> previous) {
        if (previous == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
