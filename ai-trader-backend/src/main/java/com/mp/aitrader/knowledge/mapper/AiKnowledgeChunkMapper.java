package com.mp.aitrader.knowledge.mapper;

import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiKnowledgeChunkMapper {

    @Insert("INSERT INTO ai_knowledge_chunks (doc_id, chunk_index, chunk_text, keywords, embedding_ref, created_at) " +
            "VALUES (#{docId}, #{chunkIndex}, #{chunkText}, #{keywords}, #{embeddingRef}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiKnowledgeChunk chunk);

    @Select("SELECT * FROM ai_knowledge_chunks WHERE doc_id = #{docId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "docId", column = "doc_id"),
            @Result(property = "chunkIndex", column = "chunk_index"),
            @Result(property = "chunkText", column = "chunk_text"),
            @Result(property = "keywords", column = "keywords"),
            @Result(property = "embeddingRef", column = "embedding_ref"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AiKnowledgeChunk> selectByDocId(@Param("docId") Long docId);

    @Select("SELECT * FROM ai_knowledge_chunks WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "docId", column = "doc_id"),
            @Result(property = "chunkIndex", column = "chunk_index"),
            @Result(property = "chunkText", column = "chunk_text"),
            @Result(property = "keywords", column = "keywords"),
            @Result(property = "embeddingRef", column = "embedding_ref"),
            @Result(property = "createdAt", column = "created_at")
    })
    AiKnowledgeChunk selectById(Long id);

    @Delete("DELETE FROM ai_knowledge_chunks WHERE doc_id = #{docId}")
    void deleteByDocId(@Param("docId") Long docId);
}
