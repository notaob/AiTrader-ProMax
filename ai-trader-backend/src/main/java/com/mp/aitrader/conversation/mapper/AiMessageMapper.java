package com.mp.aitrader.conversation.mapper;

import com.mp.aitrader.conversation.domain.AiMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiMessageMapper {

    @Insert("INSERT INTO ai_messages (conversation_id, role, content, message_index, token_count, created_at) " +
            "VALUES (#{conversationId}, #{role}, #{content}, #{messageIndex}, #{tokenCount}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiMessage message);

    @Select("SELECT * FROM ai_messages WHERE conversation_id = #{conversationId} ORDER BY message_index ASC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "conversationId", column = "conversation_id"),
            @Result(property = "role", column = "role"),
            @Result(property = "content", column = "content"),
            @Result(property = "messageIndex", column = "message_index"),
            @Result(property = "tokenCount", column = "token_count"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AiMessage> selectByConversationId(Long conversationId);

    @Select("SELECT * FROM ai_messages WHERE conversation_id = #{conversationId} ORDER BY message_index DESC LIMIT #{limit}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "conversationId", column = "conversation_id"),
            @Result(property = "role", column = "role"),
            @Result(property = "content", column = "content"),
            @Result(property = "messageIndex", column = "message_index"),
            @Result(property = "tokenCount", column = "token_count"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AiMessage> selectRecentMessages(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ai_messages WHERE conversation_id = #{conversationId}")
    int countByConversationId(Long conversationId);

    @Select("SELECT MAX(message_index) FROM ai_messages WHERE conversation_id = #{conversationId}")
    Integer selectMaxMessageIndex(Long conversationId);
}
