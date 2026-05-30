package com.mp.aitrader.memory.mapper;

import com.mp.aitrader.memory.domain.AiUserMemory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiUserMemoryMapper {

    @Insert("INSERT INTO ai_user_memories (user_id, memory_type, content, importance_score, confidence_score, source, is_active, last_used_at, created_at, updated_at) " +
            "VALUES (#{userId}, #{memoryType}, #{content}, #{importanceScore}, #{confidenceScore}, #{source}, #{isActive}, #{lastUsedAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiUserMemory memory);

    @Update("UPDATE ai_user_memories SET user_id = #{userId}, memory_type = #{memoryType}, content = #{content}, " +
            "importance_score = #{importanceScore}, confidence_score = #{confidenceScore}, source = #{source}, " +
            "is_active = #{isActive}, last_used_at = #{lastUsedAt}, updated_at = NOW() WHERE id = #{id}")
    void updateById(AiUserMemory memory);

    @Select("SELECT * FROM ai_user_memories WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "memoryType", column = "memory_type"),
            @Result(property = "content", column = "content"),
            @Result(property = "importanceScore", column = "importance_score"),
            @Result(property = "confidenceScore", column = "confidence_score"),
            @Result(property = "source", column = "source"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "lastUsedAt", column = "last_used_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    AiUserMemory selectById(Long id);

    @Select("SELECT * FROM ai_user_memories WHERE user_id = #{userId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "memoryType", column = "memory_type"),
            @Result(property = "content", column = "content"),
            @Result(property = "importanceScore", column = "importance_score"),
            @Result(property = "confidenceScore", column = "confidence_score"),
            @Result(property = "source", column = "source"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "lastUsedAt", column = "last_used_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<AiUserMemory> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM ai_user_memories WHERE user_id = #{userId} AND is_active = 1")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "memoryType", column = "memory_type"),
            @Result(property = "content", column = "content"),
            @Result(property = "importanceScore", column = "importance_score"),
            @Result(property = "confidenceScore", column = "confidence_score"),
            @Result(property = "source", column = "source"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "lastUsedAt", column = "last_used_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<AiUserMemory> selectActiveByUserId(@Param("userId") Long userId);

    @Update("UPDATE ai_user_memories SET is_active = 0, updated_at = NOW() WHERE id = #{id}")
    void deactivateById(@Param("id") Long id);
}
