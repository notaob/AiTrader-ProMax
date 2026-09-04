package com.mp.aitrader.agent.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mp.aitrader.agent.dto.ChatResponse;
import com.mp.aitrader.agent.dto.LangGraphChatResult;
import com.mp.aitrader.agent.dto.ReActResponse;
import com.mp.aitrader.agent.dto.TypedMemoryCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LangGraph Python 服务客户端
 * 用于调用 Python 端的 LangGraph Agent 服务
 */
@Component
@Slf4j
public class LangGraphClient {

    @Value("${ai-agent.url:http://localhost:8000}")
    private String agentServiceUrl;

    /**
     * ReAct 模式对话
     */
    public ReActResponse chatWithReAct(String message, String userId, String sessionId, List<Map<String, String>> history) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", message);
            requestBody.put("user_id", userId);
            requestBody.put("session_id", sessionId);
            requestBody.put("history", history);

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/chat")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(120000)
                    .execute();

            if (response.getStatus() == 200) {
                String body = response.body();
                Map<String, Object> result = JSONUtil.parseObj(body);

                return ReActResponse.builder()
                        .success((Boolean) result.get("success"))
                        .finalAnswer((String) result.get("answer"))
                        .chainOfThought(formatThoughtProcess((List<Map<String, Object>>) result.get("thought_process")))
                        .iterations(result.get("thought_process") != null ? ((List<?>) result.get("thought_process")).size() : 0)
                        .build();
            } else {
                log.error("LangGraph 服务调用失败: {}", response.getStatus());
                return ReActResponse.builder()
                        .success(false)
                        .finalAnswer("服务暂时不可用")
                        .build();
            }
        } catch (Exception e) {
            log.error("调用 LangGraph 服务异常", e);
            return ReActResponse.builder()
                    .success(false)
                    .finalAnswer("服务异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * RAG 模式对话
     */
    public ChatResponse chatWithRAG(String question, String userId, int topK) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", question);
            requestBody.put("user_id", userId);
            requestBody.put("top_k", topK);

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/rag")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() == 200) {
                String body = response.body();
                Map<String, Object> result = JSONUtil.parseObj(body);

                return ChatResponse.builder()
                        .success((Boolean) result.get("success"))
                        .content((String) result.get("answer"))
                        .executionTime((Integer) result.get("execution_time"))
                        .build();
            } else {
                log.error("RAG 服务调用失败: {}", response.getStatus());
                return ChatResponse.builder()
                        .success(false)
                        .content("服务暂时不可用")
                        .build();
            }
        } catch (Exception e) {
            log.error("调用 RAG 服务异常", e);
            return ChatResponse.builder()
                    .success(false)
                    .content("服务异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 策略报告模式（生成完整策略报告）
     */
    public ReActResponse chatWithStrategyMode(String message, String userId, String sessionId) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", message);
            requestBody.put("user_id", userId);
            requestBody.put("session_id", sessionId);
            requestBody.put("history", null);
            requestBody.put("mode", "strategy");  // 策略报告模式

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/chat")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(120000)
                    .execute();

            if (response.getStatus() == 200) {
                String body = response.body();
                Map<String, Object> result = JSONUtil.parseObj(body);

                return ReActResponse.builder()
                        .success((Boolean) result.get("success"))
                        .finalAnswer((String) result.get("answer"))
                        .chainOfThought(formatThoughtProcess((List<Map<String, Object>>) result.get("thought_process")))
                        .iterations(result.get("thought_process") != null ? ((List<?>) result.get("thought_process")).size() : 0)
                        .build();
            } else {
                log.error("LangGraph 服务调用失败: {}", response.getStatus());
                return ReActResponse.builder()
                        .success(false)
                        .finalAnswer("服务暂时不可用")
                        .build();
            }
        } catch (Exception e) {
            log.error("调用 LangGraph 服务异常", e);
            return ReActResponse.builder()
                    .success(false)
                    .finalAnswer("服务异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 带上下文的对话
     */
    public LangGraphChatResult chatWithContext(String message, String userId, String sessionId,
                                   List<Map<String, String>> history,
                                   Map<String, Object> state,
                                   List<String> summaries,
                                   List<String> memories,
                                   String mode) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", message);
            requestBody.put("user_id", userId);
            requestBody.put("session_id", sessionId);
            requestBody.put("history", history);
            requestBody.put("state", state);
            requestBody.put("summaries", summaries);
            requestBody.put("memories", memories != null ? memories : new ArrayList<>());
            requestBody.put("mode", mode != null ? mode : "chat");

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/chat")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(120000)
                    .execute();

            if (response.getStatus() == 200) {
                String body = response.body();
                Map<String, Object> result = JSONUtil.parseObj(body);
                String answer = (String) result.get("answer");

                // 解析 Python 端返回的记忆候选（文本列表，向后兼容）
                List<String> memoryCandidates = new ArrayList<>();
                Object candidatesObj = result.get("memory_candidates");
                if (candidatesObj instanceof List) {
                    for (Object item : (List<?>) candidatesObj) {
                        if (item != null) {
                            memoryCandidates.add(item.toString());
                        }
                    }
                }

                // 解析带类型的记忆候选
                List<TypedMemoryCandidate> typedCandidates = new ArrayList<>();
                Object typedCandidatesObj = result.get("memory_candidates_typed");
                if (typedCandidatesObj instanceof List) {
                    for (Object item : (List<?>) typedCandidatesObj) {
                        if (item instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) item;
                            String content = map.get("content") != null ? map.get("content").toString() : "";
                            String type = map.get("type") != null ? map.get("type").toString() : "preference";
                            if (!content.isEmpty()) {
                                typedCandidates.add(TypedMemoryCandidate.builder()
                                        .content(content)
                                        .memoryType(type)
                                        .build());
                            }
                        }
                    }
                }

                return LangGraphChatResult.builder()
                        .answer(answer)
                        .memoryCandidates(memoryCandidates)
                        .typedMemoryCandidates(typedCandidates)
                        .build();
            } else {
                log.error("LangGraph 服务调用失败: {}", response.getStatus());
                return LangGraphChatResult.builder()
                        .answer("AI 服务暂时繁忙，请稍后再试。")
                        .memoryCandidates(new ArrayList<>())
                        .typedMemoryCandidates(new ArrayList<>())
                        .build();
            }
        } catch (Exception e) {
            log.error("调用 LangGraph 服务异常", e);
            return LangGraphChatResult.builder()
                    .answer("AI 分析服务连接失败，请检查网络配置。")
                    .memoryCandidates(new ArrayList<>())
                    .typedMemoryCandidates(new ArrayList<>())
                    .build();
        }
    }

    /**
     * 同步知识 chunks 到 Python 向量索引（Java 保存 MySQL 后调用）
     * userId 用于知识库按用户隔离
     */
    public boolean syncChunksToVectorStore(List<Map<String, Object>> chunkData, Long userId) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chunks", chunkData);
            requestBody.put("user_id", userId);

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/rag/sync")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() == 200) {
                Map<String, Object> result = JSONUtil.parseObj(response.body());
                log.info("同步 {} 条 chunks 到向量索引", result.get("synced_count"));
                return true;
            } else {
                log.warn("同步向量索引失败: HTTP {}", response.getStatus());
                return false;
            }
        } catch (Exception e) {
            log.error("调用向量同步接口失败", e);
            return false;
        }
    }

    /**
     * 语义召回用户长期记忆（Python embedding + 向量检索，user_id 隔离）
     * 返回 [{memory_id, content, memory_type, score, similarity}]；失败返回空列表
     */
    public List<Map<String, Object>> recallMemories(Long userId, String query, int topK) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_id", userId.toString());
            requestBody.put("query", query);
            requestBody.put("top_k", topK);

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/memories/recall")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() == 200) {
                Map<String, Object> result = JSONUtil.parseObj(response.body());
                if (Boolean.TRUE.equals(result.get("success"))) {
                    Object memoriesObj = result.get("memories");
                    if (memoriesObj instanceof List) {
                        List<Map<String, Object>> memories = new ArrayList<>();
                        for (Object item : (List<?>) memoriesObj) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = (Map<String, Object>) item;
                                memories.add(m);
                            }
                        }
                        return memories;
                    }
                }
            } else {
                log.warn("语义召回失败: HTTP {}", response.getStatus());
            }
        } catch (Exception e) {
            log.error("调用语义召回接口异常", e);
        }
        return new ArrayList<>();
    }

    /**
     * 保存用户记忆向量（Java 已落库 MySQL，回调 Python 生成 embedding 入库）
     */
    public boolean syncMemorySave(Long userId, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("memories", items);

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/memories/save")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() == 200) {
                Map<String, Object> result = JSONUtil.parseObj(response.body());
                boolean ok = Boolean.TRUE.equals(result.get("success"));
                if (!ok) {
                    log.warn("Python 记忆向量写入失败: {}", response.body());
                }
                return ok;
            }
            log.warn("同步记忆向量失败: HTTP {}", response.getStatus());
        } catch (Exception e) {
            log.error("调用记忆向量保存接口异常", e);
        }
        return false;
    }

    /**
     * 删除记忆向量：优先按 memoryIds 精确删；否则按 memoryType；均空则清空该用户全部记忆向量。
     */
    public boolean syncMemoryDelete(Long userId, List<Long> memoryIds, String memoryType) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_id", userId.toString());
            if (memoryIds != null && !memoryIds.isEmpty()) {
                requestBody.put("memory_ids", memoryIds);
            } else if (memoryType != null && !memoryType.isEmpty()) {
                requestBody.put("memory_type", memoryType);
            }

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/memories/delete")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() == 200) {
                Map<String, Object> result = JSONUtil.parseObj(response.body());
                boolean ok = Boolean.TRUE.equals(result.get("success"));
                if (!ok) {
                    log.warn("Python 记忆向量删除失败: {}", response.body());
                }
                return ok;
            }
            log.warn("删除记忆向量失败: HTTP {}", response.getStatus());
        } catch (Exception e) {
            log.error("调用记忆向量删除接口异常", e);
        }
        return false;
    }

    /**
     * 调用 Python 语义摘要（LLM 压缩批量消息）；失败返回 null，由调用方降级
     */
    public String summarizeMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("messages", messages);

            HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/summarize")
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(60000)
                    .execute();

            if (response.getStatus() == 200) {
                Map<String, Object> result = JSONUtil.parseObj(response.body());
                if (Boolean.TRUE.equals(result.get("success"))) {
                    String summary = (String) result.get("summary");
                    if (summary != null && !summary.isBlank()) {
                        return summary;
                    }
                }
            } else {
                log.warn("语义摘要失败: HTTP {}", response.getStatus());
            }
        } catch (Exception e) {
            log.error("调用语义摘要接口异常", e);
        }
        return null;
    }

    /**
     * SSE 流式对话：POST /agent/chat/stream，按行消费 data 帧并回调。
     *
     * 帧协议（Python 端 app/streaming.py）：
     *   {type: "token", content} | {type: "tool", status, name, ...}
     *   | {type: "done", answer, memory_candidates_typed, ...} | {type: "error", message}
     * 阻塞执行到流结束；调用方应在独立线程运行（见 AiConversationServiceImpl#runChatStream）。
     *
     * onFrame 抛出的异常会中断消费并向上传播（用于客户端断开等场景），底层连接随之关闭。
     */
    public void chatWithContextStream(String message, String userId, String sessionId,
                                      List<Map<String, String>> history,
                                      Map<String, Object> state,
                                      List<String> summaries,
                                      List<String> memories,
                                      String mode,
                                      StreamFrameConsumer onFrame) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", message);
        requestBody.put("user_id", userId);
        requestBody.put("session_id", sessionId);
        requestBody.put("history", history);
        requestBody.put("state", state);
        requestBody.put("summaries", summaries);
        requestBody.put("memories", memories != null ? memories : new ArrayList<>());
        requestBody.put("mode", mode != null ? mode : "chat");

        HttpResponse response = HttpRequest.post(agentServiceUrl + "/agent/chat/stream")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(requestBody))
                .timeout(300000)  // 连接 + 读超时：SSE 可能长时间无帧（思考 / 工具执行）
                .execute();

        if (response.getStatus() != 200) {
            throw new IOException("Python SSE 服务返回 HTTP " + response.getStatus());
        }

        try (response;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    continue;  // 空行 / SSE 注释
                }
                if (trimmed.startsWith("data:")) {
                    String data = trimmed.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    JSONObject frame = JSONUtil.parseObj(data);
                    onFrame.accept(frame);
                }
                // event: / id: 行忽略（未使用命名事件）
            }
        }
    }

    /**
     * SSE 帧回调。可抛出异常以中断流（上层据此处理客户端断连）。
     */
    @FunctionalInterface
    public interface StreamFrameConsumer {
        void accept(JSONObject frame) throws Exception;
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        try {
            HttpResponse response = HttpRequest.get(agentServiceUrl + "/health")
                    .timeout(5000)
                    .execute();
            return response.getStatus() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 格式化思考过程
     */
    private String formatThoughtProcess(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return "无思考过程";
        }

        StringBuilder sb = new StringBuilder("=== LangGraph 执行过程 ===\n\n");
        for (Map<String, Object> step : steps) {
            String type = (String) step.get("type");
            Integer stepNum = (Integer) step.get("step");

            switch (type) {
                case "thought":
                    sb.append(String.format("步骤 %d [思考]: %s\n", stepNum, step.get("content")));
                    break;
                case "action":
                    sb.append(String.format("步骤 %d [行动]: 调用 %s, 输入: %s\n",
                            stepNum, step.get("tool"), step.get("input")));
                    break;
                case "observation":
                    sb.append(String.format("步骤 %d [观察]: %s 返回: %s\n",
                            stepNum, step.get("tool"), step.get("output")));
                    break;
            }
        }
        return sb.toString();
    }
}
