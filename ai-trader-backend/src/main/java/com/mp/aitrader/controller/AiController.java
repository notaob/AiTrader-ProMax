package com.mp.aitrader.controller;

import com.mp.aitrader.VO.AIChatVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.service.TbAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private TbAiService aiService;

    /**
     * AI 对话接口
     * URL : POST /ai/chat
     */
    @PostMapping("/chat")
    public Result<AIChatVO> chat() {
        return aiService.chat();
    }
}
