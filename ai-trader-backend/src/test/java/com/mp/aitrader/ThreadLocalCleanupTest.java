package com.mp.aitrader;

import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.conversation.controller.AiConversationController;
import com.mp.aitrader.conversation.dto.ChatMessageRequest;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.interceptor.JwtInterceptor;
import com.mp.aitrader.properties.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2 验证：Servlet 异步请求下 ThreadLocal 必须被清理（串号 / 越权隐患）。
 *
 * <p>背景：用户身份存于 {@code BaseContext} 的 ThreadLocal，由 JwtInterceptor 在
 * {@code afterCompletion} 清理。但 SSE 接口返回 {@code SseEmitter} 后请求进入 Servlet 异步模式，
 * 此时 {@code afterCompletion} <b>不保证在"设置值的那个 Servlet 线程"上执行</b> ——
 * 线程会带着上一位用户的 userId 回到 Tomcat 线程池，被后续请求复用时读到他人身份。
 * 而 {@code /moments/list} 被放行匿名访问且会读取 {@code BaseContext.getCurrentId()}
 * 计算 isLiked，一旦复用到残留线程即造成越权。
 *
 * <p>本测试是<b>纯单元测试</b>：不需要 MySQL、不需要 Redis、不需要启动 Tomcat，
 * 直接验证两处修复点（Controller 显式清理 + 拦截器匿名分支兜底清理）。
 *
 * <p>端到端验证（需后端与 Redis 启动）见 {@code scripts/verify-t2-threadlocal.ps1}。
 */
@ExtendWith(MockitoExtension.class)
class ThreadLocalCleanupTest {

    @Mock
    private AiConversationService conversationService;

    @InjectMocks
    private AiConversationController controller;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private JwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // 单测中 @PostConstruct 不会自动触发，需手工初始化并发闸门（否则 chatStream 会 NPE）
        ReflectionTestUtils.setField(controller, "maxConcurrent", 10);
        controller.initAiGate();
        BaseContext.removeCurrentId();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void chatStream_shouldClearThreadLocal_beforeReturningEmitter() {
        BaseContext.setCurrentId(123L);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("你好");
        SseEmitter emitter = controller.chatStream(1L, request);

        assertNotNull(emitter, "应返回 SseEmitter");
        // ★ 核心判据：SSE 让请求进入异步模式，afterCompletion 不保证在同一线程执行，
        //   因此必须在 Controller 返回前显式清理，否则线程带着 userId 回到 Tomcat 线程池。
        assertNull(BaseContext.getCurrentId(),
                "SSE 接口返回后必须清理 ThreadLocal，否则 Servlet 线程回到线程池会带着上一位用户的 id（串号/越权）");
    }

    @Test
    void chatStream_shouldPassCapturedUserId_insteadOfReadingThreadLater() {
        BaseContext.setCurrentId(123L);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("你好");
        controller.chatStream(1L, request);

        // 身份应在清理前捕获并显式传入，业务链路不再依赖 ThreadLocal 跨越异步边界
        verify(conversationService, timeout(3000))
                .runChatStream(eq(1L), eq(123L), eq(request), any(SseEmitter.class));
    }

    @Test
    void interceptor_shouldClearThreadLocal_onAnonymousAllowedPath() throws Exception {
        when(jwtProperties.getUserTokenName()).thenReturn("Authorization");

        // 模拟"线程池复用"：当前线程上残留着上一位用户的身份
        BaseContext.setCurrentId(123L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/moments/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = AiConversationController.class
                .getMethod("chatStream", Long.class, ChatMessageRequest.class);
        HandlerMethod handler = new HandlerMethod(controller, method);

        boolean pass = interceptor.preHandle(request, response, handler);

        assertTrue(pass, "/moments/list 应允许匿名访问");
        assertNull(BaseContext.getCurrentId(),
                "匿名放行分支必须清理 ThreadLocal，否则会读到线程池残留的上一位用户身份");
    }
}
