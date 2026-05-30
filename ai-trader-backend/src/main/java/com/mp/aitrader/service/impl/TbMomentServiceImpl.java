package com.mp.aitrader.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mp.aitrader.DTO.MomentDTO;
import com.mp.aitrader.DTO.MomentLikeDTO;
import com.mp.aitrader.VO.MomentLikeVO;
import com.mp.aitrader.VO.MomentVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.domain.TbMoment;
import com.mp.aitrader.domain.TbMomentLike;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbMomentLikeMapper;
import com.mp.aitrader.mapper.TbMomentMapper;
import com.mp.aitrader.mapper.TbUserMapper;
import com.mp.aitrader.service.TbMomentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TbMomentServiceImpl extends ServiceImpl<TbMomentMapper, TbMoment> implements TbMomentService {

    @Autowired
    private TbMomentMapper momentMapper;

    @Autowired
    private TbUserMapper userMapper;

    @Autowired
    private TbMomentLikeMapper momentLikeMapper;

    @Override
    public Result<List<MomentVO>> getMomentList() {
        Long currentUserId = BaseContext.getCurrentId();
        
        // 查询所有动态，按时间倒序
        QueryWrapper<TbMoment> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        List<TbMoment> moments = momentMapper.selectList(queryWrapper);

        if (moments == null || moments.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        // 转换为VO
        List<MomentVO> momentVOs = moments.stream().map(moment -> {
            TbUser user = userMapper.selectById(moment.getUserId());
            boolean isLiked = false;
            if (currentUserId != null) {
                // 检查当前用户是否点赞
                QueryWrapper<TbMomentLike> likeWrapper = new QueryWrapper<>();
                likeWrapper.eq("moment_id", moment.getId())
                          .eq("user_id", currentUserId);
                isLiked = momentLikeMapper.selectCount(likeWrapper) > 0;
            }

            return MomentVO.builder()
                    .id(moment.getId())
                    .userName(user != null ? user.getNickName() : "未知用户")
                    .userAvatar(user != null ? user.getIcon() : "")
                    .time(DateUtil.format(moment.getCreateTime(), "yyyy-MM-dd HH:mm"))
                    .content(moment.getContent())
                    .likes(moment.getLikes())
                    .comments(moment.getComments())
                    .isLiked(isLiked)
                    .build();
        }).collect(Collectors.toList());

        return Result.success(momentVOs);
    }

    @Override
    public Result<TbMoment> createMoment(MomentDTO momentDTO) {
        Long userId = BaseContext.getCurrentId();
        
        TbMoment moment = new TbMoment();
        moment.setUserId(userId);
        moment.setContent(momentDTO.getContent());
        moment.setLikes(0);
        moment.setComments(0);
        moment.setCreateTime(new Date());
        moment.setUpdateTime(new Date());

        momentMapper.insert(moment);

        return Result.success(moment);
    }

    @Override
    @Transactional
    public Result<MomentLikeVO> likeMoment(MomentLikeDTO momentLikeDTO) {
        Long userId = BaseContext.getCurrentId();
        Long momentId = momentLikeDTO.getId();

        // 检查动态是否存在
        TbMoment moment = momentMapper.selectById(momentId);
        if (moment == null) {
            return Result.error("动态不存在");
        }

        // 检查是否已经点赞
        QueryWrapper<TbMomentLike> likeWrapper = new QueryWrapper<>();
        likeWrapper.eq("moment_id", momentId).eq("user_id", userId);
        TbMomentLike existingLike = momentLikeMapper.selectOne(likeWrapper);

        boolean isLiked;
        if (existingLike != null) {
            // 已点赞，执行取消点赞
            momentLikeMapper.deleteById(existingLike.getId());
            moment.setLikes(Math.max(0, moment.getLikes() - 1));
            isLiked = false;
        } else {
            // 未点赞，执行点赞
            TbMomentLike newLike = new TbMomentLike();
            newLike.setMomentId(momentId);
            newLike.setUserId(userId);
            newLike.setCreateTime(new Date());
            momentLikeMapper.insert(newLike);
            moment.setLikes(moment.getLikes() + 1);
            isLiked = true;
        }

        // 更新动态点赞数
        momentMapper.updateById(moment);

        return Result.success(MomentLikeVO.builder()
                .isLiked(isLiked)
                .likes(moment.getLikes())
                .build());
    }
}
