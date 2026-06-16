package com.mp.aitrader.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName tb_user
 */
@TableName(value ="tb_user")
@Data
public class TbUser {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 密码，加密存储
     */
    private String password;

    /**
     * 昵称，默认是用户id
     */
    private String nickName;

    /**
     * 人物头像
     */
    private String icon;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * VIP等级
     */
    private Integer vipLevel;

    /**
     * AI对话次数
     */
    private Integer aiChance;

    /**
     * 积分
     */
    private Integer point;
}