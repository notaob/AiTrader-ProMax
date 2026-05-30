package com.mp.aitrader.service.impl;

import com.mp.aitrader.VO.AIChatVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import com.mp.aitrader.service.TbAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * AI 服务实现
 * 调用 Python Agent 生成策略报告
 */
@Slf4j
@Service
public class TbAiServiceImpl implements TbAiService {

    @Autowired
    private TbUserMapper userMapper;

    @Autowired
    private LangGraphClient langGraphClient;

    @Override
    @Transactional
    public Result<AIChatVO> chat() {
        Long userId = BaseContext.getCurrentId();

        // 1. 获取用户信息
        TbUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.success(AIChatVO.builder().reply("用户不存在").build());
        }

        // 2. 检查AI机会是否足够
        Integer aiChance = user.getAiChance() == null ? 0 : user.getAiChance();
        if (aiChance <= 0) {
            return Result.success(AIChatVO.builder().reply("AI交易机会不足，请先获取机会").build());
        }

        // 3. 调用 Agent 生成策略报告
        String reply = callAgent(user);

        // 4. 只有 Agent 调用成功才扣除机会
        if (!reply.startsWith("AI 服务暂时繁忙") && !reply.startsWith("AI 服务响应异常") && !reply.startsWith("AI 分析服务连接失败")) {
            user.setAiChance(aiChance - 1);
            user.setUpdateTime(new Date());
            userMapper.updateById(user);
        }

        return Result.success(AIChatVO.builder()
                .reply(reply)
                .build());
    }

    private String callAgent(TbUser user) {
        try {
            // 构造 Prompt，让 Agent 生成策略报告
            String prompt = String.format(
                    "请作为一名专业的加密货币交易专家，根据当前BTC市场数据，制定一份详细的交易策略。\n" +
                    "请使用Markdown格式输出。\n" +
                    "请包含以下内容：\n" +
                    "1. 市场趋势分析（多头/空头/震荡）\n" +
                    "2. 关键支撑位和阻力位\n" +
                    "3. 具体交易建议（包括入场点位、止损点位、止盈点位）\n\n" +
                    "【用户】\n" +
                    "用户昵称：%s",
                    user.getNickName()
            );

            log.info("发送 Agent 请求，用户: {}", user.getNickName());

            // 调用 Python Agent（策略报告模式）
            var response = langGraphClient.chatWithStrategyMode(
                    prompt,
                    user.getId().toString(),
                    "strategy_" + System.currentTimeMillis()
            );

            if (response.isSuccess()) {
                return response.getFinalAnswer();
            } else {
                log.error("Agent 返回错误: {}", response.getError());
                return "AI 服务暂时繁忙，请稍后再试。";
            }

        } catch (Exception e) {
            log.error("Agent 调用失败", e);
            return "AI 分析服务连接失败，请检查网络配置。";
        }
    }
}
