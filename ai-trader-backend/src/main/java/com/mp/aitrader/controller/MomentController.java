package com.mp.aitrader.controller;

import com.mp.aitrader.DTO.MomentDTO;
import com.mp.aitrader.DTO.MomentLikeDTO;
import com.mp.aitrader.VO.MomentLikeVO;
import com.mp.aitrader.VO.MomentVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.domain.TbMoment;
import com.mp.aitrader.service.TbMomentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/moments")
public class MomentController {

    @Autowired
    private TbMomentService momentService;

    /**
     * 获取动态列表
     * URL : GET /api/moments/list
     */
    @GetMapping("/list")
    public Result<List<MomentVO>> getMomentList() {
        return momentService.getMomentList();
    }

    /**
     * 发布动态
     * URL : POST /api/moments/create
     */
    @PostMapping("/create")
    public Result<TbMoment> createMoment(@RequestBody MomentDTO momentDTO) {
        return momentService.createMoment(momentDTO);
    }

    /**
     * 点赞/取消点赞
     * URL : POST /api/moments/like
     */
    @PostMapping("/like")
    public Result<MomentLikeVO> likeMoment(@RequestBody MomentLikeDTO momentLikeDTO) {
        return momentService.likeMoment(momentLikeDTO);
    }
}
