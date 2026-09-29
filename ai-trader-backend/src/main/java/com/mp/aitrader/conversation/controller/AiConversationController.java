package com.mp.aitrader.conversation.controller;

import com.mp.aitrader.VO.Result;
import com.mp.aitrader.conversation.dto.*;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.metric.AiMetrics;
import com.mp.aitrader.trace.TraceSupport;
import cn.hutool.json.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/ai/conversations")
public class AiConversationController {

    /**
     * SSE 超时（ms）。与 {@code spring.mvc.async.request-timeout}（120000）以及 Python 侧读超时保持一致。
     * 原值为 300s：一个卡死的请求会占住线程与 Python 连接 5 分钟，10 个就能把服务拖到不可用。
     */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    @Autowired
    private AiConversationService conversationService;

    @Autowired
    private AiMetrics aiMetrics;

    /** 同时进行的 AI 对话上限：超限时快速失败，而不是把所有请求堆在内存里一起等死。 */
    @Value("${ai.chat.max-concurrent:10}")
    private int maxConcurrent;

    private Semaphore aiGate;

    /**
     * 有界线程池。原实现是 {@code Executors.newCachedThreadPool()}（无上限），
     * 而每个 SSE 请求会占住一个线程直到流结束 —— 并发上来会直接打爆线程数。
     */
    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            namedFactory("chat-stream-worker"),
            new ThreadPoolExecutor.AbortPolicy());

    private static ThreadFactory namedFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    @PostConstruct
    public void initAiGate() {
        this.aiGate = new Semaphore(maxConcurrent);
    }

    @PostMapping("/{id}/chat/stream")
    public SseEmitter chatStream(@PathVariable("id") Long conversationId,
                                 @RequestBody ChatMessageRequest request) {
        Long userId = BaseContext.getCurrentId();
        try {
            log.info("用户 {} 在会话 {} 发起流式对话", userId, conversationId);

            // 并发闸门：拿不到许可立即失败，避免线程与 Python 侧 LLM 配额被一起拖垮
            if (!aiGate.tryAcquire()) {
                aiMetrics.incrementGateRejected();
                log.warn("AI 对话并发已达上限 {}，拒绝会话 {} 的请求", maxConcurrent, conversationId);
                return errorEmitter("当前咨询人数较多，请稍后再试");
            }

            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
            emitter.onTimeout(() ->
                    log.warn("SSE 超时：conversation={}, timeout={}ms", conversationId, SSE_TIMEOUT_MS));
            emitter.onError(e ->
                    log.warn("SSE 异常：conversation={}, err={}", conversationId, e.getMessage()));

            try {
                // userId 在此处取值后显式传入，业务链路不再依赖 ThreadLocal 跨越异步边界；
                // traceId 同样需要显式传递，否则 worker 线程里的日志串不起来
                streamExecutor.execute(TraceSupport.wrap(() -> {
                    try {
                        conversationService.runChatStream(conversationId, userId, request, emitter);
                    } finally {
                        // ★ 必须在 worker 的 finally 里释放：controller 方法在 execute() 后立刻返回，
                        //   若放在方法末尾，任务还没开始跑许可就被还回去了。
                        aiGate.release();
                    }
                }));
            } catch (RejectedExecutionException e) {
                // 线程池拒绝时必须归还许可，否则闸门会永久泄漏，服务将再也无法接受任何对话
                aiGate.release();
                aiMetrics.incrementGateRejected();
                log.error("流式任务被线程池拒绝：conversation={}", conversationId, e);
                return errorEmitter("服务繁忙，请稍后再试");
            }
            return emitter;
        } finally {
            // SSE 使请求进入 Servlet 异步模式，此时拦截器的 afterCompletion 不保证在同一线程执行，
            // 若不在此显式清理，ThreadLocal 会随 Servlet 线程回到线程池，被后续请求读到（串号/越权）。
            BaseContext.removeCurrentId();
        }
    }

    /** 立即下发 error 帧并结束：沿用既有 SSE 帧协议（type=error），前端无需改动。 */
    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter();
        try {
            JSONObject error = new JSONObject();
            error.set("type", "error");
            error.set("message", message);
            emitter.send(error.toString());
        } catch (Exception ignored) {
            log.warn("下发 error 帧失败：{}", message);
        }
        emitter.complete();
        return emitter;
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdownNow();
    }

    @PostMapping
    public Result<ConversationResponse> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 创建新会话", userId);
        ConversationResponse response = conversationService.createConversation(userId, request);
        return Result.success(response);
    }

    @GetMapping
    public Result<List<ConversationResponse>> getUserConversations() {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 获取会话列表", userId);
        List<ConversationResponse> conversations = conversationService.getUserConversations(userId);
        return Result.success(conversations);
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageResponse>> getConversationMessages(@PathVariable("id") Long conversationId) {
        log.info("获取会话 {} 的消息列表", conversationId);
        List<MessageResponse> messages = conversationService.getConversationMessages(conversationId);
        return Result.success(messages);
    }

    @PostMapping("/{id}/chat")
    public Result<ChatResponse> chat(@PathVariable("id") Long conversationId, @RequestBody ChatMessageRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 在会话 {} 中发送消息", userId, conversationId);
        ChatResponse response = conversationService.chat(conversationId, userId, request);
        return Result.success(response);
    }

    @GetMapping("/{id}/state")
    public Result<SessionStateResponse> getSessionState(@PathVariable("id") Long conversationId) {
        log.info("获取会话 {} 的状态", conversationId);
        SessionStateResponse state = conversationService.getSessionState(conversationId);
        return Result.success(state);
    }
}
