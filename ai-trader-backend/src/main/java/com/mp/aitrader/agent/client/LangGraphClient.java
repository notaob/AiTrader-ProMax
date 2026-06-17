package com.mp.aitrader.agent.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.mp.aitrader.agent.dto.ChatResponse;
import com.mp.aitrader.agent.dto.LangGraphChatResult;
import com.mp.aitrader.agent.dto.ReActResponse;
import com.mp.aitrader.agent.dto.TypedMemoryCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
