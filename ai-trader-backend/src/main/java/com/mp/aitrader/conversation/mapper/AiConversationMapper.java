package com.mp.aitrader.conversation.mapper;

import com.mp.aitrader.conversation.domain.AiConversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiConversationMapper {

    @Insert("INSERT INTO ai_conversations (user_id, title, scene_type, status, last_message_at, created_at, updated_at) " +
            "VALUES (#{userId}, #{title}, #{sceneType}, #{status}, #{lastMessageAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiConversation conversation);

    @Select("SELECT * FROM ai_conversations WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "sceneType", column = "scene_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "lastMessageAt", column = "last_message_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    AiConversation selectById(Long id);

    @Select("SELECT * FROM ai_conversations WHERE user_id = #{userId} AND status = 'active' ORDER BY updated_at DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "sceneType", column = "scene_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "lastMessageAt", column = "last_message_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<AiConversation> selectByUserId(Long userId);

    @Update("UPDATE ai_conversations SET title = #{title}, scene_type = #{sceneType}, status = #{status}, " +
            "last_message_at = #{lastMessageAt}, updated_at = NOW() WHERE id = #{id}")
    void update(AiConversation conversation);

    @Update("UPDATE ai_conversations SET status = 'archived', updated_at = NOW() WHERE id = #{id}")
    void archiveById(Long id);
}
