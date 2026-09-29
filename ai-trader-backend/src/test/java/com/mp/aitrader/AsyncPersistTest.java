package com.mp.aitrader;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.agent.dto.LangGraphChatResult;
import com.mp.aitrader.agent.dto.TypedMemoryCandidate;
import com.mp.aitrader.conversation.dto.ChatMessageRequest;
import com.mp.aitrader.conversation.dto.ConversationResponse;
import com.mp.aitrader.conversation.dto.CreateConversationRequest;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.conversation.service.AiSummaryService;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import com.mp.aitrader.memory.service.AiMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T5 验证：摘要与记忆持久化不得阻塞本次对话（done 帧）。
 *
 * <p>背景：{@code persistAiReply} 位于 {@code sendDoneFrame} 之前，
 * 里面串行跑着 1 次 LLM 摘要 + 2~3 次 embedding 调用 —— 用户 token 早已流完，
 * 却还要等这些"只影响下一次对话"的任务才收到结束信号。
 *
 * <p>判据：把这些任务换成 sleep，直接观测 {@code chat()} 的耗时。
 *
 * <p><b>例外</b>：{@code constraint}（风控/止损规则）必须同步 ——
 * 用户刚说"止损 5%"，下一轮 AI 就得立刻知道，延迟生效有真实业务风险。
 *
 * <p>运行前提：本机 MySQL（本测试不触碰 Redis）。
 */
@SpringBootTest
class AsyncPersistTest {

    private static final long TASK_LATENCY_MS = 2_000L;

    @MockBean
    private AiSummaryService summaryService;

    @MockBean
    private AiMemoryService memoryService;

    @MockBean
    private LangGraphClient langGraphClient;

    @Autowired
    private AiConversationService conversationService;

    @Autowired
    private TbUserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private final List<Long> conversationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TbUser user = new TbUser();
        user.setPhone("t5-" + System.nanoTime());
        user.setEmail("t5-" + System.nanoTime() + "@aitrader.local");
        user.setPassword("test-only");
        user.setNickName("T5异步化测试用户");
        user.setVipLevel(0);
        user.setAiChance(100);
        user.setPoint(0);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userMapper.insert(user);
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        conversationIds.forEach(this::cleanupConversation);
        userMapper.deleteById(userId);
    }

    @Test
    void chat_shouldNotWaitForSummaryGeneration() throws Exception {
        when(langGraphClient.chatWithContext(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(LangGraphChatResult.builder()
                        .answer("测试回答")
                        .typedMemoryCandidates(new ArrayList<>())
                        .build());
        when(summaryService.shouldCreateSummary(anyLong())).thenReturn(true);
        doAnswer(inv -> {
            Thread.sleep(TASK_LATENCY_MS);
            return null;
        }).when(summaryService).generateAndSaveSummary(anyLong());

        Long cid = newConversation();
        long t0 = System.currentTimeMillis();
        conversationService.chat(cid, userId, req("你好"));
        long elapsed = System.currentTimeMillis() - t0;

        System.out.printf("[T5] 摘要异步化：摘要延迟=%dms → chat() 实际耗时=%dms（供 README 填写）%n",
                TASK_LATENCY_MS, elapsed);

        // ★ 核心判据：摘要 sleep 2s，chat() 却应在半秒内返回
        assertTrue(elapsed < TASK_LATENCY_MS / 2,
                "chat() 不应等待摘要生成（摘要 sleep " + TASK_LATENCY_MS + "ms），实际 " + elapsed + "ms");

        // 但任务最终确实会被执行
        verify(summaryService, timeout(10_000)).generateAndSaveSummary(cid);
    }

    @Test
    void constraintMemory_shouldBeSynchronous_whilePreferenceAsync() throws Exception {
        when(langGraphClient.chatWithContext(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(LangGraphChatResult.builder()
                        .answer("测试回答")
                        .typedMemoryCandidates(List.of(
                                TypedMemoryCandidate.builder()
                                        .content("每笔止损 5%").memoryType("constraint").build(),
                                TypedMemoryCandidate.builder()
                                        .content("偏好波段交易").memoryType("preference").build()))
                        .build());
        // 两类记忆写入都 sleep，用耗时区分"是否被 chat() 等待"
        doAnswer(inv -> {
            Thread.sleep(TASK_LATENCY_MS);
            return null;
        }).when(memoryService).saveChatMemory(any(), anyString(), eq("constraint"));
        doAnswer(inv -> {
            Thread.sleep(TASK_LATENCY_MS);
            return null;
        }).when(memoryService).saveChatMemory(any(), anyString(), eq("preference"));

        Long cid = newConversation();
        long t0 = System.currentTimeMillis();
        conversationService.chat(cid, userId, req("你好"));
        long elapsed = System.currentTimeMillis() - t0;

        System.out.printf("[T5] 记忆分流：constraint 同步 / preference 异步，单项延迟=%dms → chat() 实际耗时=%dms（供 README 填写）%n",
                TASK_LATENCY_MS, elapsed);

        // constraint 同步：chat() 必须等它（≥1 个任务耗时）
        assertTrue(elapsed >= TASK_LATENCY_MS - 200,
                "constraint（风控规则）应同步写入，chat() 必须等它，实际 " + elapsed + "ms");
        // preference 异步：chat() 不应再等第二个（否则会接近 2 个任务耗时）
        assertTrue(elapsed < TASK_LATENCY_MS * 2 - 300,
                "preference 应异步写入，chat() 不应等它，实际 " + elapsed + "ms");

        verify(memoryService, times(1)).saveChatMemory(eq(userId), eq("每笔止损 5%"), eq("constraint"));
        verify(memoryService, timeout(10_000)).saveChatMemory(eq(userId), eq("偏好波段交易"), eq("preference"));
    }

    private Long newConversation() {
        CreateConversationRequest createReq = new CreateConversationRequest();
        createReq.setTitle("T5 异步化测试");
        createReq.setSceneType("chat");
        ConversationResponse conv = conversationService.createConversation(userId, createReq);
        conversationIds.add(conv.getId());
        return conv.getId();
    }

    private ChatMessageRequest req(String message) {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage(message);
        return request;
    }

    private void cleanupConversation(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM ai_messages WHERE conversation_id = ?", conversationId);
            jdbcTemplate.update("DELETE FROM ai_conversation_summaries WHERE conversation_id = ?", conversationId);
            jdbcTemplate.update("DELETE FROM ai_session_state WHERE conversation_id = ?", conversationId);
            jdbcTemplate.update("DELETE FROM ai_conversations WHERE id = ?", conversationId);
        } catch (Exception e) {
            System.out.println("[T5] 清理测试数据失败（不影响断言）：" + e.getMessage());
        }
    }
}
