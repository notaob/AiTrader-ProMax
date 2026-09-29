package com.mp.aitrader.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求入口生成 traceId：上游有则沿用（便于跨服务串联），没有则新建。
 *
 * <p>放在 Filter 而非 Interceptor：Filter 早于 Interceptor 执行，
 * 这样鉴权、业务、异常处理的日志全都带上 traceId。
 *
 * <p>异步请求（SSE）在初始派发结束时会走到 finally 清理 MDC —— 这与
 * {@code AiConversationController} 中清理 ThreadLocal 的道理一致：
 * Servlet 线程会回到线程池，不能带着上下文。异步线程需要的 MDC
 * 由 {@link TraceSupport#wrap(Runnable)} 显式传递。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceSupport.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceSupport.newTraceId();
        }
        MDC.put(TraceSupport.TRACE_ID, traceId);
        response.setHeader(TraceSupport.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceSupport.TRACE_ID);
        }
    }
}
