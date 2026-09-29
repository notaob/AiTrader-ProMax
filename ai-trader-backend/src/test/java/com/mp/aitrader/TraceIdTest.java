package com.mp.aitrader;

import com.mp.aitrader.conversation.controller.AiConversationController;
import com.mp.aitrader.conversation.dto.ChatMessageRequest;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.trace.TraceIdFilter;
import com.mp.aitrader.trace.TraceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * T9 验证：traceId 全链路。
 *
 * <p>两条关键性质：
 * <ol>
 *   <li><b>异步线程必须能拿到 traceId</b> —— MDC 是 ThreadLocal，不会自动跨线程。
 *       本项目大量使用异步（SSE 编排、AI 后台任务），拿不到就串不起日志。</li>
 *   <li><b>请求结束必须清理 MDC</b> —— 与 ThreadLocal 用户身份是同一个道理：
 *       Servlet 线程回到线程池后不能带着上一次请求的上下文。</li>
 * </ol>
 *
 * <p>纯单元测试，不需要 MySQL / Redis / Tomcat。
 */
@ExtendWith(MockitoExtension.class)
class TraceIdTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Mock
    private AiConversationService conversationService;

    @InjectMocks
    private AiConversationController controller;

    @BeforeEach
    void initController() {
        ReflectionTestUtils.setField(controller, "maxConcurrent", 10);
        controller.initAiGate();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 端到端验证"接线"：用真实线程池跑一次 SSE 提交，确认 worker 线程真的拿到了 traceId。
     * （前面几条只验证了 TraceSupport 自身，不保证调用点真的包了 wrap。）
     */
    @Test
    void chatStream_shouldSubmitTaskThatCarriesTraceId() throws Exception {
        MDC.put(TraceSupport.TRACE_ID, "t-1");
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            seen.set(MDC.get(TraceSupport.TRACE_ID));
            latch.countDown();
            return null;
        }).when(conversationService).runChatStream(any(), any(), any(), any());

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("你好");
        controller.chatStream(1L, request);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "worker 应被真实线程池执行");
        assertEquals("t-1", seen.get(), "SSE worker 线程必须继承 traceId，否则流式链路日志串不起来");
    }

    @Test
    void shouldGenerateTraceId_andClearMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ai/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {
        });

        String traceId = response.getHeader(TraceSupport.HEADER);
        assertNotNull(traceId, "应生成 traceId 并写入响应头");
        assertFalse(traceId.isBlank());
        // ★ 与 T2 同理：请求结束后必须清理，否则线程回到线程池会带着上一次请求的 traceId
        assertNull(MDC.get(TraceSupport.TRACE_ID), "请求结束后必须清理 MDC");
    }

    @Test
    void shouldReuseUpstreamTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ai/conversations");
        request.addHeader(TraceSupport.HEADER, "upstream-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {
        });

        assertEquals("upstream-123", response.getHeader(TraceSupport.HEADER));
    }

    /** 核心：MDC 不会自动跨线程，必须显式传递。 */
    @Test
    void shouldPropagateMdcToAsyncThread() throws Exception {
        MDC.put(TraceSupport.TRACE_ID, "abc-999");
        AtomicReference<String> seen = new AtomicReference<>();

        Thread worker = new Thread(TraceSupport.wrap(() ->
                seen.set(MDC.get(TraceSupport.TRACE_ID))));
        worker.start();
        worker.join();

        assertEquals("abc-999", seen.get(), "异步线程应能读到同一个 traceId");
    }

    @Test
    void shouldNotLeakMdc_afterWrappedTaskRunsOnAnotherThread() throws Exception {
        MDC.put(TraceSupport.TRACE_ID, "abc-999");
        AtomicReference<String> afterTask = new AtomicReference<>("not-run");

        Thread worker = new Thread(TraceSupport.wrap(() -> {
            assertEquals("abc-999", MDC.get(TraceSupport.TRACE_ID));
            // 任务结束后应恢复原上下文（此处为原线程的空上下文）
            afterTask.set("done");
        }));
        worker.start();
        worker.join();

        assertEquals("done", afterTask.get());
        // 原线程的 MDC 不受影响
        assertEquals("abc-999", MDC.get(TraceSupport.TRACE_ID));
    }
}
