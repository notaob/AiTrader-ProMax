package com.mp.aitrader;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.agent.dto.LangGraphChatResult;
import com.mp.aitrader.conversation.dto.ChatMessageRequest;
import com.mp.aitrader.conversation.dto.ConversationResponse;
import com.mp.aitrader.conversation.dto.CreateConversationRequest;
import com.mp.aitrader.conversation.service.AiConversationService;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T1 验证：远程 IO（LLM 调用）不得占用数据库连接。
 *
 * <p>背景：原实现把整条对话链路包在 {@code @Transactional} 里，而其中的 LLM 调用最长可达 120s。
 * 事务期间数据库连接一直被占用 —— 少量并发即可耗尽连接池，导致登录等无关接口一起阻塞。
 *
 * <p><b>验证手法</b>：把慢 LLM 换成 sleep，并把<b>连接池压到 5</b>，观察总耗时：
 *
 * <pre>
 *   改造前：事务包住 LLM → 连接被霸占整个 LLM 周期 → 20 个请求被 5 个连接串行成 4 批 → 约 4 × LLM 延迟
 *   改造后：LLM 在事务外 → 连接只在写入瞬间占用 → 20 个请求完全并行 → 约 1 × LLM 延迟
 * </pre>
 *
 * <p>判据是<b>总耗时 &lt; 1.5 × LLM 延迟</b>（另要求全部请求成功）。相比采样连接池活跃数，
 * 这个判据不依赖 MBean 是否可用，也不会因瞬时抖动而误判。
 *
 * <p><b>注意</b>：每个线程必须使用<b>独立会话</b>。若所有线程共用一个会话，它们会争抢
 * ai_session_state / ai_conversations 的同一行产生行锁等待，那是测试设计引入的干扰，不是被测行为。
 *
 * <p>运行前提：本机 MySQL 已启动（本测试不触碰 Redis）。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1",
        // 超时给足，避免把"拿不到连接"变成失败原因 —— 判据只看总耗时
        "spring.datasource.hikari.connection-timeout=30000"
})
class TransactionBoundaryTest {

    private static final int CONCURRENCY = 20;
    private static final long LLM_LATENCY_MS = 3_000L;
    private static final long SAMPLE_AT_MS = 1_000L;
    /** 改造后 20 个请求完全并行，耗时 ≈ 1 × LLM 延迟；改造前会被 5 个连接串行成 4 批 ≈ 4 ×。 */
    private static final long MAX_ELAPSED_MS = (long) (LLM_LATENCY_MS * 1.5);

    @MockBean
    private LangGraphClient langGraphClient;

    @Autowired
    private AiConversationService conversationService;

    @Autowired
    private TbUserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void chat_shouldNotHoldDbConnection_duringSlowLlmCall() throws Exception {
        // 采样点：LLM 调用期间是否处于数据库事务中（决定连接是否被占用）
        AtomicInteger txActiveDuringLlm = new AtomicInteger();

        // 模拟慢 LLM。真实场景下单次调用最长 120s，这里压缩到 3s 以便快速回归。
        when(langGraphClient.chatWithContext(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        txActiveDuringLlm.incrementAndGet();
                    }
                    Thread.sleep(LLM_LATENCY_MS);
                    return LangGraphChatResult.builder()
                            .answer("测试回答")
                            .typedMemoryCandidates(new ArrayList<>())
                            .build();
                });

        assertTrue(dataSource instanceof HikariDataSource, "预期使用 HikariCP，实际=" + dataSource.getClass());

        // 先取一次连接触发 HikariPool 初始化，随后才能读到 MXBean（仅用于报告，不作为判据）
        HikariPoolMXBean pool = null;
        try {
            dataSource.getConnection().close();
            pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
        } catch (Exception ignored) {
            // MXBean 不可用不影响判据
        }

        Long userId = insertTestUser();
        List<Long> conversationIds = new ArrayList<>();
        try {
            // 每个线程一个独立会话，排除行锁干扰
            for (int i = 0; i < CONCURRENCY; i++) {
                CreateConversationRequest createReq = new CreateConversationRequest();
                createReq.setTitle("T1 事务边界测试-" + i);
                createReq.setSceneType("chat");
                ConversationResponse conv = conversationService.createConversation(userId, createReq);
                conversationIds.add(conv.getId());
            }

            AtomicInteger finished = new AtomicInteger();
            Queue<String> failureMsgs = new ConcurrentLinkedQueue<>();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(CONCURRENCY);
            ExecutorService workers = Executors.newFixedThreadPool(CONCURRENCY);

            for (int i = 0; i < CONCURRENCY; i++) {
                final Long cid = conversationIds.get(i);
                workers.submit(() -> {
                    try {
                        start.await();
                        ChatMessageRequest msg = new ChatMessageRequest();
                        msg.setMessage("你好");
                        conversationService.chat(cid, userId, msg);
                        finished.incrementAndGet();
                    } catch (Exception e) {
                        // 失败不计入 finished —— 改造前正是这里会大量命中
                        failureMsgs.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            long t0 = System.currentTimeMillis();
            start.countDown();

            // 此刻 20 个请求都卡在"LLM 调用"中
            Thread.sleep(SAMPLE_AT_MS);
            int activeDuringLlm = (pool != null) ? pool.getActiveConnections() : -1;

            boolean completed = done.await(60, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - t0;
            workers.shutdownNow();

            assertTrue(completed, "20 个并发请求应在 60s 内全部结束");
            failureMsgs.stream().distinct().limit(3)
                    .forEach(m -> System.out.println("[T1] 失败样本 -> " + m));

            System.out.printf(
                    "[T1] 并发=%d, LLM延迟=%dms, 连接池=5 → 成功=%d, LLM期间活跃连接=%d, LLM期间事务激活数=%d, 总耗时=%dms（阈值%dms，供 README 填写）%n",
                    CONCURRENCY, LLM_LATENCY_MS, finished.get(), activeDuringLlm, txActiveDuringLlm.get(),
                    elapsedMs, MAX_ELAPSED_MS);

            assertEquals(CONCURRENCY, finished.get(), "全部请求都应成功，不应因连接耗尽而失败");

            // ★ 核心判据：连接池仅 5 个。若事务霸占连接，20 个请求会被串行成 4 批，
            // 耗时约 4 × LLM 延迟；改造后完全并行，耗时约 1 × LLM 延迟。
            assertTrue(elapsedMs < MAX_ELAPSED_MS,
                    "20 并发应完全并行，总耗时应 < " + MAX_ELAPSED_MS + "ms；实际 " + elapsedMs
                            + "ms，说明 LLM 调用期间事务仍占用数据库连接（连接池=5, LLM 延迟=" + LLM_LATENCY_MS + "ms）");

            // ★ 最直接的表达：远程调用期间根本不该存在数据库事务
            assertEquals(0, txActiveDuringLlm.get(),
                    "LLM 调用期间不应存在数据库事务；实际有 " + txActiveDuringLlm.get() + " 个线程处于事务中");

            if (activeDuringLlm >= 0) {
                assertTrue(activeDuringLlm <= 1,
                        "LLM 调用进行中时，数据库连接占用应 ≤ 1，实际=" + activeDuringLlm);
            }
        } finally {
            conversationIds.forEach(this::cleanupConversation);
            userMapper.deleteById(userId);
        }
    }

    private Long insertTestUser() {
        TbUser user = new TbUser();
        user.setPhone("t1-" + System.nanoTime());
        user.setEmail("t1-" + System.nanoTime() + "@aitrader.local");
        user.setPassword("test-only");
        user.setNickName("T1事务边界测试用户");
        user.setVipLevel(0);
        user.setAiChance(100);
        user.setPoint(0);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 清理测试会话。
     * 用 JdbcTemplate 而非 Mapper：conversation 相关 Mapper 是纯注解接口（未继承 BaseMapper），
     * 为避免仅为测试而改动生产代码，这里直接用 SQL 清理。失败只记录，不影响断言结果。
     */
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
            System.out.println("[T1] 清理测试数据失败（不影响断言）：" + e.getMessage());
        }
    }
}
