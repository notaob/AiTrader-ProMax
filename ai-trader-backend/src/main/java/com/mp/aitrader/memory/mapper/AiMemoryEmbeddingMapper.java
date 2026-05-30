package com.mp.aitrader.memory.mapper;

import com.mp.aitrader.memory.domain.AiMemoryEmbedding;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiMemoryEmbeddingMapper {

    @Insert("INSERT INTO ai_memory_embeddings (memory_id, embedding_ref, embedding_model, created_at) " +
            "VALUES (#{memoryId}, #{embeddingRef}, #{embeddingModel}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiMemoryEmbedding embedding);

    @Select("SELECT * FROM ai_memory_embeddings WHERE memory_id = #{memoryId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "memoryId", column = "memory_id"),
            @Result(property = "embeddingRef", column = "embedding_ref"),
            @Result(property = "embeddingModel", column = "embedding_model"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AiMemoryEmbedding> selectByMemoryId(@Param("memoryId") Long memoryId);

    @Delete("DELETE FROM ai_memory_embeddings WHERE memory_id = #{memoryId}")
    void deleteByMemoryId(@Param("memoryId") Long memoryId);
}
