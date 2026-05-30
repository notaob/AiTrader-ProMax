package com.mp.aitrader.memory.controller;

import com.mp.aitrader.VO.Result;
import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.memory.domain.AiUserMemory;
import com.mp.aitrader.memory.service.AiMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai/memories")
public class AiMemoryController {

    @Autowired
    private AiMemoryService memoryService;

    @GetMapping
    public Result<List<AiUserMemory>> getUserMemories() {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 获取记忆列表", userId);
        List<AiUserMemory> memories = memoryService.getUserMemories(userId);
        return Result.success(memories);
    }

    @PostMapping("/rebuild")
    public Result<String> rebuildMemoryIndex() {
        Long userId = BaseContext.getCurrentId();
        log.info("用户 {} 重建记忆索引", userId);
        return Result.success("记忆索引重建任务已提交");
    }
}
