package com.mp.aitrader.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mp.aitrader.DTO.CommentDTO;
import com.mp.aitrader.DTO.MomentDTO;
import com.mp.aitrader.DTO.MomentLikeDTO;
import com.mp.aitrader.VO.CommentVO;
import com.mp.aitrader.VO.MomentLikeVO;
import com.mp.aitrader.VO.MomentVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.domain.TbMoment;

import java.util.List;

public interface TbMomentService extends IService<TbMoment> {

    Result<List<MomentVO>> getMomentList(int page, int size);

    Result<TbMoment> createMoment(MomentDTO momentDTO);

    Result<MomentLikeVO> likeMoment(MomentLikeDTO momentLikeDTO);

    Result<List<CommentVO>> getComments(Long momentId);

    Result<CommentVO> addComment(CommentDTO commentDTO);
}
