package com.mp.aitrader.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tb_promotion")
public class TbPromotion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String actionText;
    private String actionColor;
    private String type;
    private Integer requiredPoints;
}
