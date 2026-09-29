package com.mp.aitrader;

import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 验证：AI 机会（ai_chance）扣减的并发安全性。
 *
 * <p>背景：原实现是「select → 校验 → updateById」的读-改-写三步，
 * 并发下两个请求可同时读到 ai_chance=1 并双双通过校验，最终写成 0 —— 用户白用一次。
 * 而 ai_chance 可用 1000 积分兑换（{@code TbMarketServiceImpl.exchangeAiChance}），具备资金属性。
 *
 * <p>改造：改为单条 {@code UPDATE ... SET ai_chance = ai_chance - 1 WHERE ai_chance > 0}，
 * 把"判断"与"扣减"压进同一条 SQL，以影响行数判定成败，天然并发安全且无需加锁。
 *
 * <p>运行前提：本机 MySQL + Redis 已启动（与 AiTraderApplicationTests 要求一致）。
 */
@SpringBootTest
class AiChanceConcurrencyTest {

    private static final int CONCURRENCY = 20;

    @Autowired
    private TbUserMapper userMapper;

    @Test
    void deductAiChance_shouldSucceedExactlyOnce_whenBalanceIsOne() throws Exception {
        Long userId = insertTestUser(1);
        try {
            AtomicInteger success = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(CONCURRENCY);
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

            for (int i = 0; i < CONCURRENCY; i++) {
                pool.submit(() -> {
                    try {
                        start.await();                    // 尽量同时起跑，制造真实竞争
                        if (userMapper.deductAiChance(userId) > 0) {
                            success.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // 单线程失败不应中断压测，最终以 success 计数判定
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            pool.shutdownNow();

            assertTrue(finished, "20 个并发任务应在 30s 内全部结束");
            assertEquals(1, success.get(),
                    "余额为 1 时，20 并发扣减必须恰好成功 1 次；若 >1 说明仍是「读-改-写」，存在超扣");
            assertEquals(0, userMapper.selectById(userId).getAiChance(),
                    "最终余额必须为 0，不能被扣成负数");
        } finally {
            userMapper.deleteById(userId);
        }
    }

    private Long insertTestUser(int aiChance) {
        TbUser user = new TbUser();
        user.setPhone("t3-" + System.nanoTime());
        user.setEmail("t3-" + System.nanoTime() + "@aitrader.local");
        user.setPassword("test-only");
        user.setNickName("T3并发测试用户");
        user.setVipLevel(0);
        user.setAiChance(aiChance);
        user.setPoint(0);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userMapper.insert(user);
        return user.getId();
    }
}
