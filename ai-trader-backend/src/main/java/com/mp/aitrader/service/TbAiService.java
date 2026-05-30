package com.mp.aitrader.service;


import com.mp.aitrader.VO.AIChatVO;
import com.mp.aitrader.VO.Result;

public interface TbAiService {
    Result<AIChatVO> chat();
}
