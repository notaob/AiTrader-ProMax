package com.mp.aitrader.knowledge.controller;

import com.mp.aitrader.VO.Result;
import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import com.mp.aitrader.knowledge.domain.AiKnowledgeDoc;
import com.mp.aitrader.knowledge.service.AiKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController {

    @Autowired
    private AiKnowledgeService knowledgeService;

    @PostMapping("/upload")
    public Result<String> uploadKnowledgeDocument(@RequestBody AiKnowledgeDoc doc,
                                                   @RequestParam List<String> chunks) {
        log.info("上传知识文档: {}", doc.getTitle());
        knowledgeService.uploadDocument(doc, chunks);
        return Result.success("文档上传成功");
    }

    @GetMapping
    public Result<List<AiKnowledgeDoc>> getAllDocs() {
        log.info("获取知识文档列表");
        List<AiKnowledgeDoc> docs = knowledgeService.getAllDocs();
        return Result.success(docs);
    }

    @GetMapping("/{id}/chunks")
    public Result<List<AiKnowledgeChunk>> getChunksByDocId(@PathVariable("id") Long docId) {
        log.info("获取文档 {} 的分片", docId);
        List<AiKnowledgeChunk> chunks = knowledgeService.getChunksByDocId(docId);
        return Result.success(chunks);
    }
}
