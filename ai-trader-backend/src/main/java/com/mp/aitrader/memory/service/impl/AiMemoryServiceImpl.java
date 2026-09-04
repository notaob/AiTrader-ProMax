package com.mp.aitrader.memory.service.impl;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.mapper.AiUserMemoryMapper;
import com.mp.aitrader.memory.service.AiMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户长期记忆：MySQL 只负责存储，向量召回/写入收口到 Python 服务。
 * - 保存（insert）后回调 /agent/memories/save 写向量；
 * - 失效/清除时回调 /agent/memories/delete 清向量；
 * - 召回走 /agent/memories/recall（embedding 语义检索），Python 不可用时回退关键词匹配。
 */
@Slf4j
@Service
public class AiMemoryServiceImpl implements AiMemoryService {

    private static final int MEMORY_CONTENT_MAX = 500;

    @Autowired
    private AiUserMemoryMapper userMemoryMapper;

    @Autowired
    private LangGraphClient langGraphClient;

    @Override
    public void extractMemory(Long userId, String userMessage, String aiResponse) {
        // 已废弃：记忆提取现在由 Python 端 AI 分类完成，通过 typedMemoryCandidates 返回
        // 保留接口兼容性，不做任何操作
    }

    @Override
    public List<AiUserMemory> getUserMemories(Long userId) {
        return userMemoryMapper.selectActiveByUserId(userId);
    }

    @Override
    public void deactivateMemory(Long memoryId) {
        AiUserMemory memory = userMemoryMapper.selectById(memoryId);
        userMemoryMapper.deactivateById(memoryId);
        if (memory != null) {
            syncVectorDelete(memory.getUserId(), Collections.singletonList(memoryId), null);
        }
        log.info("停用记忆 {}", memoryId);
    }

    @Override
    public List<AiUserMemory> recallMemories(Long userId, String query, int limit) {
        // Stage2：语义召回（Python embedding + 向量检索，user_id 隔离）
        List<Map<String, Object>> remote = langGraphClient.recallMemories(userId, query, limit);
        if (!remote.isEmpty()) {
            List<AiUserMemory> recalled = remote.stream()
                    .map(item -> {
                        AiUserMemory m = new AiUserMemory();
                        m.setUserId(userId);
                        m.setContent(strValue(item.get("content")));
                        m.setMemoryType(strValue(item.getOrDefault("memory_type", "preference")));
                        Object id = item.get("memory_id");
                        if (id instanceof Number) {
                            m.setId(((Number) id).longValue());
                        }
                        return m;
                    })
                    .filter(m -> !m.getContent().isEmpty())
                    .collect(Collectors.toList());
            if (!recalled.isEmpty()) {
                log.info("语义召回用户 {} 记忆 {} 条", userId, recalled.size());
                return recalled;
            }
        }
        // 降级：Python 不可用 / 无向量时，回退关键词匹配兜底（迁移期老记忆仍可召回）
        log.info("语义召回为空，回退关键词匹配 (user={})", userId);
        return fallbackKeywordRecall(userId, query, limit);
    }

    private List<AiUserMemory> fallbackKeywordRecall(Long userId, String query, int limit) {
        List<AiUserMemory> activeMemories = userMemoryMapper.selectActiveByUserId(userId);
        if (activeMemories == null || activeMemories.isEmpty()) {
            return new ArrayList<>();
        }
        String lowerQuery = query == null ? "" : query.toLowerCase();
        return activeMemories.stream()
                .filter(m -> m.getContent() != null && m.getContent().toLowerCase().contains(lowerQuery))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void saveMemoryCandidate(Long userId, String text, String source) {
        // 去重检查：语义召回看是否已有高度相似的记忆
        List<AiUserMemory> existing = recallMemories(userId, text, 3);
        for (AiUserMemory m : existing) {
            if (m.getContent() != null && (m.getContent().contains(text) || text.contains(m.getContent()))) {
                log.info("记忆已存在，跳过: {}", text);
                return;
            }
        }

        // 根据 source 映射 memoryType
        String type;
        switch (source) {
            case "goal":
                type = "goal";
                break;
            case "constraint":
                type = "constraint";
                break;
            case "preference":
            default:
                type = "preference";
                break;
        }

        AiUserMemory memory = buildMemory(userId, type, text, "python_agent",
                BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.6));
        userMemoryMapper.insert(memory);
        syncVectorSave(userId, memory);
        log.info("为用户 {} 保存 Python 端记忆候选 [{}]: {}", userId, source, text);
    }

    @Override
    public void saveProfileAnswer(Long userId, String text, String memoryType) {
        AiUserMemory memory = buildMemory(userId, memoryType, text, "profile_option",
                BigDecimal.valueOf(0.9), BigDecimal.valueOf(1.0));
        userMemoryMapper.insert(memory);
        syncVectorSave(userId, memory);
        log.info("画像引导: 为用户 {} 直接保存画像回答 [{}]: {}", userId, memoryType, text);
    }

    @Override
    public void saveChatMemory(Long userId, String text, String memoryType) {
        // constraint（风控/止损类）属于安全规则：旧规则必须作废，只保留最新一条，
        // 避免 AI 把已废弃的旧止损线也当作有效约束回答用户。
        if ("constraint".equalsIgnoreCase(memoryType)) {
            int deactivated = userMemoryMapper.deactivateByType(userId, memoryType);
            if (deactivated > 0) {
                log.info("用户 {} 停用 {} 条旧 [constraint] 规则记忆", userId, deactivated);
            }
            // 同步清理该类型旧向量，避免失效规则仍被语义召回
            syncVectorDelete(userId, null, memoryType);

            AiUserMemory memory = buildMemory(userId, memoryType, text, "ai_classified",
                    BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.8));
            userMemoryMapper.insert(memory);
            syncVectorSave(userId, memory);
            log.info("用户 {} 保存 AI 分类记忆 [constraint]: {}", userId, text);
            return;
        }

        // preference / goal 属于画像事实：同类别允许并存多条（如"看好 BTC 现货"+"风格稳健长线"），
        // 仅做语义级去重，避免出现时覆盖早前事实导致长期记忆丢内容（20 轮评测已复现）。
        if (hasNearDuplicate(userId, memoryType, text)) {
            log.info("用户 {} 已有高度相似的 [{}] 记忆，跳过: {}", userId, memoryType, text);
            return;
        }

        AiUserMemory memory = buildMemory(userId, memoryType, text, "ai_classified",
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.8));
        userMemoryMapper.insert(memory);
        syncVectorSave(userId, memory);
        log.info("用户 {} 保存 AI 分类记忆 [{}]: {}", userId, memoryType, text);
    }

    /** 检查同类型活跃记忆里是否已有高度相似的条目（去重，不覆盖）。 */
    private boolean hasNearDuplicate(Long userId, String memoryType, String text) {
        List<AiUserMemory> activeMemories = userMemoryMapper.selectActiveByUserId(userId);
        for (AiUserMemory m : activeMemories) {
            if (memoryType == null ? m.getMemoryType() == null
                    : memoryType.equalsIgnoreCase(m.getMemoryType())) {
                if (isSimilarText(m.getContent(), text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSimilarText(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        if (x.equals(y) || x.contains(y) || y.contains(x)) {
            return true;
        }
        // 短文本不做模糊判断（避免误吞不同事实）；长文本按共现字符占比近似判同义
        if (x.length() < 8 || y.length() < 8) {
            return false;
        }
        int common = 0;
        for (char c : x.toCharArray()) {
            if (y.indexOf(c) >= 0) {
                common++;
            }
        }
        double ratio = (double) common / Math.min(x.length(), y.length());
        return ratio >= 0.7;
    }

    @Override
    public boolean hasCompleteProfile(Long userId) {
        return getMissingProfileCategories(userId).isEmpty();
    }

    @Override
    public List<String> getMissingProfileCategories(Long userId) {
        List<String> existingTypes = userMemoryMapper.selectDistinctMemoryTypes(userId);
        List<String> missing = new ArrayList<>();

        if (!existingTypes.contains("preference")) {
            missing.add("交易偏好");
        }
        if (!existingTypes.contains("goal")) {
            missing.add("交易目标");
        }
        if (!existingTypes.contains("constraint")) {
            missing.add("风控规则");
        }

        log.info("用户 {} 画像检查: 已有类型={}, 缺失类别={}", userId, existingTypes, missing);
        return missing;
    }

    @Override
    public int clearUserMemories(Long userId) {
        int count = userMemoryMapper.deactivateAllByUserId(userId);
        // 清除该用户全部记忆向量
        syncVectorDelete(userId, null, null);
        log.info("用户 {} 清除 {} 条记忆", userId, count);
        return count;
    }

    // ---------- 私有辅助 ----------

    private AiUserMemory buildMemory(Long userId, String memoryType, String text, String source,
                                     BigDecimal importance, BigDecimal confidence) {
        AiUserMemory memory = new AiUserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(memoryType);
        memory.setContent(text.length() > MEMORY_CONTENT_MAX ? text.substring(0, MEMORY_CONTENT_MAX) : text);
        memory.setImportanceScore(importance);
        memory.setConfidenceScore(confidence);
        memory.setSource(source);
        memory.setIsActive(1);
        memory.setLastUsedAt(LocalDateTime.now());
        return memory;
    }

    /** 记忆落库后把向量写入 Python 索引（doc id = MySQL memory_id）。 */
    private void syncVectorSave(Long userId, AiUserMemory memory) {
        if (memory.getId() == null) {
            log.warn("记忆 id 为空，跳过向量同步: {}", memory.getContent());
            return;
        }
        Map<String, Object> item = new HashMap<>();
        item.put("memory_id", memory.getId());
        item.put("user_id", userId);
        item.put("content", memory.getContent());
        item.put("memory_type", memory.getMemoryType());
        boolean ok = langGraphClient.syncMemorySave(userId, Collections.singletonList(item));
        if (!ok) {
            log.warn("记忆向量同步失败，仅落库 MySQL: memoryId={}", memory.getId());
        }
    }

    /** 记忆失效/清除时同步清理向量：优先 ids，其次按 memoryType，全空则清空该用户全部向量。 */
    private void syncVectorDelete(Long userId, List<Long> memoryIds, String memoryType) {
        boolean ok = langGraphClient.syncMemoryDelete(userId, memoryIds, memoryType);
        if (!ok) {
            log.warn("记忆向量删除失败: userId={}, ids={}, type={}", userId, memoryIds, memoryType);
        }
    }

    private static String strValue(Object o) {
        return o == null ? "" : o.toString();
    }
}
