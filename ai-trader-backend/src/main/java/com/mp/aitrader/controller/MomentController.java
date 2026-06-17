package com.mp.aitrader.controller;

import com.mp.aitrader.DTO.CommentDTO;
import com.mp.aitrader.DTO.MomentDTO;
import com.mp.aitrader.DTO.MomentLikeDTO;
import com.mp.aitrader.VO.CommentVO;
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
     * 获取动态列表（分页）
     */
    @GetMapping("/list")
    public Result<List<MomentVO>> getMomentList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return momentService.getMomentList(page, size);
    }

    /**
     * 发布动态
     */
    @PostMapping("/create")
    public Result<TbMoment> createMoment(@RequestBody MomentDTO momentDTO) {
        return momentService.createMoment(momentDTO);
    }

    /**
     * 点赞/取消点赞
     */
    @PostMapping("/like")
    public Result<MomentLikeVO> likeMoment(@RequestBody MomentLikeDTO momentLikeDTO) {
        return momentService.likeMoment(momentLikeDTO);
    }

    /**
     * 获取评论列表
     */
    @GetMapping("/{id}/comments")
    public Result<List<CommentVO>> getComments(@PathVariable("id") Long momentId) {
        return momentService.getComments(momentId);
    }

    /**
     * 发表评论
     */
    @PostMapping("/comment")
    public Result<CommentVO> addComment(@RequestBody CommentDTO commentDTO) {
        return momentService.addComment(commentDTO);
    }
}
