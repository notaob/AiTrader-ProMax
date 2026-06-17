package com.mp.aitrader.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("tb_moment_comment")
public class TbMomentComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long momentId;
    private Long userId;
    private String content;
    private Date createTime;
}
