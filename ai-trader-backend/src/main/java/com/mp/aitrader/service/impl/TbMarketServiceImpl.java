package com.mp.aitrader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mp.aitrader.DTO.ExchangeDTO;
import com.mp.aitrader.VO.PromotionVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.domain.TbPromotion;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.domain.TbUserGiftClaim;
import com.mp.aitrader.mapper.TbPromotionMapper;
import com.mp.aitrader.mapper.TbUserGiftClaimMapper;
import com.mp.aitrader.mapper.TbUserMapper;
import com.mp.aitrader.service.TbMarketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TbMarketServiceImpl extends ServiceImpl<TbPromotionMapper, TbPromotion> implements TbMarketService {

    @Autowired
    private TbPromotionMapper promotionMapper;

    @Autowired
    private TbUserMapper userMapper;

    @Autowired
    private TbUserGiftClaimMapper userGiftClaimMapper;

    @Override
    public Result<List<PromotionVO>> getPromotionList() {
        List<TbPromotion> promotions = promotionMapper.selectList(null);
        
        List<PromotionVO> promotionVOs = promotions.stream().map(p -> PromotionVO.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .actionText(p.getActionText())
                .actionColor(p.getActionColor())
                .type(p.getType())
                .requiredPoints(p.getRequiredPoints())
                .build()
        ).collect(Collectors.toList());

        return Result.success(promotionVOs);
    }

    @Override
    @Transactional
    public Result<String> claimWelcomeGift() {
        Long userId = BaseContext.getCurrentId();
        
        // 检查是否已经领取过
        QueryWrapper<TbUserGiftClaim> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("gift_type", "welcome_gift");
        if (userGiftClaimMapper.selectCount(queryWrapper) > 0) {
            return Result.error("您已经领取过新手礼包了");
        }

        // 记录领取
        TbUserGiftClaim claim = new TbUserGiftClaim();
        claim.setUserId(userId);
        claim.setGiftType("welcome_gift");
        claim.setClaimTime(new Date());
        userGiftClaimMapper.insert(claim);

        // 发放奖励：10 次 AI 机会
        TbUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        user.setAiChance((user.getAiChance() == null ? 0 : user.getAiChance()) + 10);
        user.setUpdateTime(new Date());
        userMapper.updateById(user);

        return Result.success("新手礼包领取成功");
    }

    @Override
    @Transactional
    public Result<String> exchangeAiChance(ExchangeDTO exchangeDTO) {
        Long userId = BaseContext.getCurrentId();
        
        // 固定消耗 1000 积分
        int pointsRequired = 1000;

        TbUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        Integer userPoints = user.getPoint() == null ? 0 : user.getPoint();
        if (userPoints < pointsRequired) {
            return Result.error("积分不足，需要 " + pointsRequired + " 积分");
        }

        // 扣除积分，增加 1 次 AI 机会
        user.setPoint(userPoints - pointsRequired);
        user.setAiChance((user.getAiChance() == null ? 0 : user.getAiChance()) + 1);
        user.setUpdateTime(new Date());
        userMapper.updateById(user);

        return Result.success("兑换成功，获得 1 次AI交易机会");
    }
}
