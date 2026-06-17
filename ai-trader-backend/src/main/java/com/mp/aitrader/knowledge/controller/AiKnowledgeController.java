package com.mp.aitrader.knowledge.controller;

import com.mp.aitrader.VO.Result;
import com.mp.aitrader.knowledge.domain.AiKnowledgeChunk;
import com.mp.aitrader.knowledge.domain.AiKnowledgeDoc;
import com.mp.aitrader.knowledge.service.AiKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController {

    @Autowired
    private AiKnowledgeService knowledgeService;

    @PostMapping("/upload")
    public Result<String> uploadKnowledgeFile(@RequestParam("file") MultipartFile file,
                                               @RequestParam("userId") Long userId) {
        String filename = file.getOriginalFilename();
        log.info("上传知识文件: {}, userId: {}", filename, userId);

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                return Result.error("文件内容为空");
            }

            // 从文件名提取标题（去掉扩展名）
            String title = filename != null
                    ? filename.replaceAll("\\.(txt|md|markdown)$", "")
                    : "未命名文档";

            // 构建文档对象
            AiKnowledgeDoc doc = new AiKnowledgeDoc();
            doc.setTitle(title);
            doc.setDocType("article");
            doc.setSource(filename);
            doc.setUserId(userId);

            // 按双换行拆分段落
            List<String> chunks = Arrays.stream(content.split("\n\n+"))
                    .filter(s -> !s.trim().isEmpty())
                    .toList();
            // 如果整篇没有分段，按固定长度切分
            if (chunks.size() <= 1 && content.length() > 500) {
                chunks = splitByLength(content, 500);
            }

            knowledgeService.uploadDocument(doc, chunks);
            return Result.success("文档上传成功");
        } catch (IOException e) {
            log.error("读取文件内容失败", e);
            return Result.error("读取文件失败: " + e.getMessage());
        }
    }

    private List<String> splitByLength(String text, int maxLen) {
        List<String> result = new java.util.ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + maxLen, text.length());
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > pos) {
                    end = lastNewline + 1;
                }
            }
            String chunk = text.substring(pos, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
            pos = end;
        }
        return result;
    }

    @GetMapping
    public Result<List<AiKnowledgeDoc>> getAllDocs(@RequestParam(required = false) Long userId) {
        log.info("获取知识文档列表, userId: {}", userId);
        List<AiKnowledgeDoc> docs = knowledgeService.getAllDocs();
        if (userId != null) {
            docs = docs.stream().filter(d -> userId.equals(d.getUserId())).toList();
        }
        return Result.success(docs);
    }

    @GetMapping("/{id}/chunks")
    public Result<List<AiKnowledgeChunk>> getChunksByDocId(@PathVariable("id") Long docId) {
        log.info("获取文档 {} 的分片", docId);
        List<AiKnowledgeChunk> chunks = knowledgeService.getChunksByDocId(docId);
        return Result.success(chunks);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteDoc(@PathVariable("id") Long docId,
                                     @RequestParam("userId") Long userId) {
        log.info("删除知识文档 id={}, userId={}", docId, userId);
        try {
            knowledgeService.deleteDocument(docId, userId);
            return Result.success("文档已删除");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
