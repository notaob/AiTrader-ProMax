package com.mp.aitrader.controller;

import com.mp.aitrader.DTO.ExchangeDTO;
import com.mp.aitrader.VO.PromotionVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.service.TbMarketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/market")
public class MarketController {

    @Autowired
    private TbMarketService marketService;

    /**
     * 获取活动列表
     * URL : GET /market/promotions
     */
    @GetMapping("/promotions")
    public Result<List<PromotionVO>> getPromotions() {
        return marketService.getPromotionList();
    }

    /**
     * 领取新手礼包
     * URL : POST /market/gift/claim
     */
    @PostMapping("/gift/claim")
    public Result<String> claimWelcomeGift() {
        return marketService.claimWelcomeGift();
    }

    /**
     * 积分兑换 AI 次数
     * URL : POST /market/exchange/ai
     */
    @PostMapping("/exchange/ai")
    public Result<String> exchangeAiChance(@RequestBody ExchangeDTO exchangeDTO) {
        return marketService.exchangeAiChance(exchangeDTO);
    }
}
