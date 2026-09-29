package com.mp.aitrader.conversation.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mp.aitrader.conversation.dto.ConversationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会话列表缓存（Cache-Aside）。
 *
 * <p><b>为什么缓存这个</b>：会话列表是高频读（每次进首页都要拉）、低频结构变更（新增/归档）的接口。
 *
 * <p>三个经典问题在这里的处理：<ul>
 *   <li><b>穿透</b>：查不到的用户也缓存空列表（TTL 更短），避免每次都打 DB。</li>
 *   <li><b>雪崩</b>：TTL 加随机抖动，避免一批 key 在同一时刻集体过期。</li>
 *   <li><b>击穿</b>：本场景不需要互斥锁 —— key 是<b>用户维度</b>（每人一个 key），
 *       天然分散，不存在"单个热点 key 过期瞬间全部打到 DB"的问题。
 *       若为全局热点 key（如首页榜单），才需要 setnx 互斥重建。</li>
 * </ul>
 *
 * <p><b>一致性取舍（重要）</b>：只在<b>结构变更</b>（新建 / 归档）时删除缓存，
 * <b>不为每条新消息失效</b>。因为每发一条消息都会更新 {@code updated_at}，
 * 若逐条失效则缓存形同虚设；而会话列表的排序延迟 60 秒是可接受的。
 * 新增会话必须立即可见，因此新建后立刻删除缓存。
 */
@Slf4j
@Service
public class AiConversationCache {

    public static final String KEY_PREFIX = "ai:conversations:";
    /** 基础 TTL（秒）。 */
    public static final long TTL_SECONDS = 60;
    /** TTL 随机抖动上限（秒）：打散过期时间，防雪崩。 */
    public static final long TTL_JITTER_SECONDS = 30;
    /** 空列表 TTL（秒）：比正常更短，防穿透的同时减少"长期空白"的观感误差。 */
    public static final long EMPTY_TTL_SECONDS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AiConversationCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** 命中返回列表；未命中返回 {@code null}（调用方据此回源查库）。 */
    public List<ConversationResponse> get(Long userId) {
        String json = redisTemplate.opsForValue().get(key(userId));
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConversationResponse>>() {
            });
        } catch (Exception e) {
            // 缓存内容损坏不应影响主链路：当作未命中，并顺手清掉脏数据
            log.warn("会话列表缓存解析失败，按未命中处理: userId={}, err={}", userId, e.getMessage());
            evict(userId);
            return null;
        }
    }

    public void put(Long userId, List<ConversationResponse> conversations) {
        boolean empty = conversations == null || conversations.isEmpty();
        Duration ttl = empty
                ? Duration.ofSeconds(EMPTY_TTL_SECONDS)
                : Duration.ofSeconds(TTL_SECONDS + ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS));
        try {
            redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(
                    empty ? List.of() : conversations), ttl);
        } catch (Exception e) {
            log.warn("写入会话列表缓存失败（不影响主链路）: userId={}, err={}", userId, e.getMessage());
        }
    }

    /** 结构变更后删除缓存：先更新 DB、再删缓存；删除失败由 TTL 兜底。 */
    public void evict(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception e) {
            log.warn("删除会话列表缓存失败（由 TTL 兜底）: userId={}, err={}", userId, e.getMessage());
        }
    }

    public String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
