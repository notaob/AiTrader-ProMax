package com.mp.aitrader.knowledge.mapper;

import com.mp.aitrader.knowledge.domain.AiKnowledgeDoc;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiKnowledgeDocMapper {

    @Insert("INSERT INTO ai_knowledge_docs (doc_type, title, source, status, created_at, updated_at) " +
            "VALUES (#{docType}, #{title}, #{source}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiKnowledgeDoc doc);

    @Update("UPDATE ai_knowledge_docs SET doc_type = #{docType}, title = #{title}, source = #{source}, " +
            "status = #{status}, updated_at = NOW() WHERE id = #{id}")
    void updateById(AiKnowledgeDoc doc);

    @Select("SELECT * FROM ai_knowledge_docs WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "docType", column = "doc_type"),
            @Result(property = "title", column = "title"),
            @Result(property = "source", column = "source"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    AiKnowledgeDoc selectById(Long id);

    @Select("SELECT * FROM ai_knowledge_docs")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "docType", column = "doc_type"),
            @Result(property = "title", column = "title"),
            @Result(property = "source", column = "source"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<AiKnowledgeDoc> selectAll();

    @Select("SELECT * FROM ai_knowledge_docs WHERE doc_type = #{docType}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "docType", column = "doc_type"),
            @Result(property = "title", column = "title"),
            @Result(property = "source", column = "source"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<AiKnowledgeDoc> selectByType(@Param("docType") String docType);
}
