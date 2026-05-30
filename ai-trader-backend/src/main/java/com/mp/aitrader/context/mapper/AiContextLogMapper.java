package com.mp.aitrader.context.mapper;

import com.mp.aitrader.context.domain.AiContextLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiContextLogMapper {

    @Insert("INSERT INTO ai_context_logs (conversation_id, user_message_id, scene_type, used_summary_ids, used_memory_ids, used_knowledge_ids, retrieval_score_avg, prompt_token_estimate, trim_action, validation_status, created_at) " +
            "VALUES (#{conversationId}, #{userMessageId}, #{sceneType}, #{usedSummaryIds}, #{usedMemoryIds}, #{usedKnowledgeIds}, #{retrievalScoreAvg}, #{promptTokenEstimate}, #{trimAction}, #{validationStatus}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiContextLog log);

    @Select("SELECT * FROM ai_context_logs WHERE conversation_id = #{conversationId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "conversationId", column = "conversation_id"),
            @Result(property = "userMessageId", column = "user_message_id"),
            @Result(property = "sceneType", column = "scene_type"),
            @Result(property = "usedSummaryIds", column = "used_summary_ids"),
            @Result(property = "usedMemoryIds", column = "used_memory_ids"),
            @Result(property = "usedKnowledgeIds", column = "used_knowledge_ids"),
            @Result(property = "retrievalScoreAvg", column = "retrieval_score_avg"),
            @Result(property = "promptTokenEstimate", column = "prompt_token_estimate"),
            @Result(property = "trimAction", column = "trim_action"),
            @Result(property = "validationStatus", column = "validation_status"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AiContextLog> selectByConversationId(@Param("conversationId") Long conversationId);
}
