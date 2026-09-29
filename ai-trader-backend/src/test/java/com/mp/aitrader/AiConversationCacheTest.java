package com.mp.aitrader;

import com.mp.aitrader.conversation.cache.AiConversationCache;
import com.mp.aitrader.conversation.dto.ConversationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务缓存验证：会话列表 Cache-Aside。
 *
 * <p>覆盖缓存三件套里真正落地的两条（穿透 / 雪崩），以及一致性所需的失效动作。
 * 纯单元测试（Mockito），<b>不需要 Redis / MySQL</b>。
 */
@ExtendWith(MockitoExtension.class)
class AiConversationCacheTest {

    private static final String KEY = AiConversationCache.KEY_PREFIX + "1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private AiConversationCache cache;

    @BeforeEach
    void setUp() {
        // lenient：evict 相关用例不会用到 opsForValue，避免 UnnecessaryStubbing 报错
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cache = new AiConversationCache(redisTemplate);
    }

    @Test
    void shouldReturnNull_onCacheMiss() {
        when(valueOps.get(KEY)).thenReturn(null);
        assertNull(cache.get(1L), "未命中应返回 null，由调用方回源查库");
    }

    @Test
    void shouldReturnCachedList_onCacheHit() {
        when(valueOps.get(KEY)).thenReturn("[{\"id\":7,\"title\":\"T\"}]");

        List<ConversationResponse> result = cache.get(1L);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        assertEquals("T", result.get(0).getTitle());
    }

    /** 防穿透：查不到也缓存（空列表），且 TTL 更短。 */
    @Test
    void shouldCacheEmptyList_withShorterTtl() {
        cache.put(1L, List.of());

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(KEY), eq("[]"), ttl.capture());
        assertEquals(AiConversationCache.EMPTY_TTL_SECONDS, ttl.getValue().getSeconds(),
                "空列表应写入缓存以挡住穿透，且 TTL 短于正常值");
    }

    /** 防雪崩：TTL 带随机抖动，避免一批 key 同时过期。 */
    @Test
    void shouldApplyJitterToTtl() {
        cache.put(1L, List.of(ConversationResponse.builder().id(1L).title("x").build()));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(KEY), anyString(), ttl.capture());

        long seconds = ttl.getValue().getSeconds();
        assertTrue(seconds >= AiConversationCache.TTL_SECONDS
                        && seconds < AiConversationCache.TTL_SECONDS + AiConversationCache.TTL_JITTER_SECONDS,
                "TTL 应落在 [60, 90) 之间以打散过期时间，实际=" + seconds);
    }

    /** 一致性：结构变更后必须删除缓存（先更库、再删缓存）。 */
    @Test
    void shouldDeleteKey_onEvict() {
        cache.evict(1L);
        verify(redisTemplate).delete(KEY);
    }

    /** 缓存内容损坏不能影响主链路：按未命中处理并清掉脏数据。 */
    @Test
    void shouldTreatCorruptedJsonAsMiss_andEvictDirtyKey() {
        when(valueOps.get(KEY)).thenReturn("not-a-json");

        assertNull(cache.get(1L));
        verify(redisTemplate).delete(KEY);
    }
}
