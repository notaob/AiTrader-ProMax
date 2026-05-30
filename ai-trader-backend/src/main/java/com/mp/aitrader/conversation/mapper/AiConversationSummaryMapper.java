package com.mp.aitrader.conversation.mapper;

import com.mp.aitrader.conversation.domain.AiConversationSummary;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiConversationSummaryMapper {

    @Insert("INSERT INTO ai_conversation_summaries (conversation_id, start_message_index, end_message_index, summary_text, summary_type, created_at) " +
            "VALUES (#{conversationId}, #{startMessageIndex}, #{endMessageIndex}, #{summaryText}, #{summaryType}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiConversationSummary summary);

    @Select("SELECT * FROM ai_conversation_summaries WHERE conversation_id = #{conversationId} ORDER BY created_at DESC LIMIT 1")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "conversationId", column = "conversation_id"),
            @Result(property = "startMessageIndex", column = "start_message_index"),
            @Result(property = "endMessageIndex", column = "end_message_index"),
            @Result(property = "summaryText", column = "summary_text"),
            @Result(property = "summaryType", column = "summary_type"),
            @Result(property = "createdAt", column = "created_at")
    })
    AiConversationSummary selectLatestByConversationId(Long conversationId);

    @Select("SELECT * FROM ai_conversation_summaries WHERE conversation_id = #{conversationId} ORDER BY created_at DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "conversationId", column = "conversation_id"),
            @Result(property = "startMessageIndex", column = "start_message_index"),
            @Result(property = "endMessageIndex", column = "end_message_index"),
            @Result(property = "summaryText", column = "summary_text"),
            @Result(property = "summaryType", column = "summary_type"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AiConversationSummary> selectByConversationId(Long conversationId);
}
