package com.mp.aitrader.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName(value ="tb_user_gift_claim")
@Data
public class TbUserGiftClaim {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String giftType;

    private Date claimTime;
}
