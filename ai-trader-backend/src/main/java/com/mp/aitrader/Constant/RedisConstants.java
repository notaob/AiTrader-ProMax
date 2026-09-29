package com.mp.aitrader.Constant;

/**
 * Redis key 与 TTL 常量。
 *
 * <p>仅保留本项目真实使用的条目。早期从课程示例沿用的商铺 / 秒杀 / 签到 / 博客点赞等常量已清除 ——
 * 它们是死代码，且出现在一个交易类项目里会让人误判工程质量。
 */
public class RedisConstants {

    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;

    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;
}
