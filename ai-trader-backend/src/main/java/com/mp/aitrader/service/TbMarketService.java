package com.mp.aitrader.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mp.aitrader.DTO.ExchangeDTO;
import com.mp.aitrader.VO.PromotionVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.domain.TbPromotion;

import java.util.List;

public interface TbMarketService extends IService<TbPromotion> {

    Result<List<PromotionVO>> getPromotionList();

    Result<String> claimWelcomeGift();

    Result<String> exchangeAiChance(ExchangeDTO exchangeDTO);
}
