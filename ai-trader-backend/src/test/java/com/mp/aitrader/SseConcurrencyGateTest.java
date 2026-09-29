package com.mp.aitrader;

import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.conversation.controller.AiConversationController;
import com.mp.aitrader.conversation.dto.ChatMessageRequest;
import com.mp.aitrader.conversation.service.AiConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T6 验证：SSE 对话的并发上限与资源治理。
 *
 * <p>背景：原实现用 {@code Executors.newCachedThreadPool()}（无上限）承载 SSE 编排，
 * 而每个请求会占住一个线程直到流结束（原超时 300s）。并发上来会同时打爆
 * 本服务的线程数与 Python 侧的 LLM 配额，形成雪崩。
 *
 * <p>改造：有界线程池 + {@code Semaphore} 并发闸门，超限时<b>快速失败</b>（返回 error 帧）。
 *
 * <p>本测试是纯单元测试，不需要 MySQL / Redis / Tomcat。
 */
@ExtendWith(MockitoExtension.class)
class SseConcurrencyGateTest {

    @Mock
    private AiConversationService conversationService;

    @InjectMocks
    private AiConversationController controller;

    @BeforeEach
    void setUp() {
        // 单测中 @PostConstruct 不会自动触发，需手工初始化闸门
        ReflectionTestUtils.setField(controller, "maxConcurrent", 1);
        controller.initAiGate();
        ReflectionTestUtils.setField(controller, "aiMetrics",
                new com.mp.aitrader.metric.AiMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        BaseContext.removeCurrentId();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldFastFail_whenConcurrentLimitReached() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        doAnswer(inv -> {
            hold.await(10, TimeUnit.SECONDS);
            return null;
        }).when(conversationService).runChatStream(any(), any(), any(), any());

        controller.chatStream(1L, req());   // 占住唯一许可，worker 被 hold 卡住
        controller.chatStream(2L, req());   // 超限 → 应快速失败

        // ★ 核心判据：被拒的请求根本不会进入业务执行。
        // 若没有闸门，第二个请求会排队等待，内存里堆积大量挂起请求 —— 那正是雪崩的起点。
        Thread.sleep(300);
        verify(conversationService, times(1)).runChatStream(any(), any(), any(), any());

        hold.countDown();
    }

    @Test
    void shouldReleasePermit_afterWorkerFinishes() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        doAnswer(inv -> {
            hold.await(10, TimeUnit.SECONDS);
            return null;
        }).when(conversationService).runChatStream(any(), any(), any(), any());

        controller.chatStream(1L, req());
        verify(conversationService, timeout(2000).times(1)).runChatStream(any(), any(), any(), any());

        hold.countDown();
        Thread.sleep(500);                  // 等 worker 走完 finally 归还许可

        controller.chatStream(3L, req());   // 许可已归还 → 应放行
        verify(conversationService, timeout(3000).times(2)).runChatStream(any(), any(), any(), any());
    }

    private ChatMessageRequest req() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("你好");
        return request;
    }
}
