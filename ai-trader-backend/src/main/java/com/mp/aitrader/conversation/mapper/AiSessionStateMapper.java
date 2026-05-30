package com.mp.aitrader.conversation.mapper;

import com.mp.aitrader.conversation.domain.AiSessionState;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiSessionStateMapper {

    @Insert("INSERT INTO ai_session_state (conversation_id, current_intent, current_mode, current_step, state_json, updated_at) " +
            "VALUES (#{conversationId}, #{currentIntent}, #{currentMode}, #{currentStep}, #{stateJson}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiSessionState state);

    @Select("SELECT * FROM ai_session_state WHERE conversation_id = #{conversationId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "conversationId", column = "conversation_id"),
            @Result(property = "currentIntent", column = "current_intent"),
            @Result(property = "currentMode", column = "current_mode"),
            @Result(property = "currentStep", column = "current_step"),
            @Result(property = "stateJson", column = "state_json"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    AiSessionState selectByConversationId(Long conversationId);

    @Update("UPDATE ai_session_state SET current_intent = #{currentIntent}, current_mode = #{currentMode}, " +
            "current_step = #{currentStep}, state_json = #{stateJson}, updated_at = NOW() " +
            "WHERE conversation_id = #{conversationId}")
    void update(AiSessionState state);

    @Delete("DELETE FROM ai_session_state WHERE conversation_id = #{conversationId}")
    void deleteByConversationId(Long conversationId);
}
